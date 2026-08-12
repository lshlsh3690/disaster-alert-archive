package com.disaster.alert.alertapi.domain.notification.service;

import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmSendService {

    // 웹푸시는 data-only 메시지로만 발송한다 (webpush.notification 페이로드를 넣지 않음).
    // notification 페이로드가 있으면 Firebase JS SDK가 서비스워커에서 알림을 자동으로 한 번 더 표시하여,
    // onBackgroundMessage가 띄우는 알림과 합쳐져 알림이 2번(두 번째는 제목·본문 없는 빈 알림) 표시된다.
    // 실제 표시는 firebase-messaging-sw.js의 onBackgroundMessage에서 전담한다.

    // 이 서비스는 발송만 하지 않는다 — Firebase 가 영구 무효로 판정한 토큰을 그 자리에서
    // DeadTokenCleanupService 로 넘겨 저장소에서 지우게 한다. 죽음을 판정하는 지식(MessagingErrorCode)이
    // 여기에 있어서, 정리 트리거를 호출부로 올리면 오케스트레이터가 Firebase SDK 타입까지 알아야 한다.

    // 로그에 남길 토큰 접두사 길이. 배치 실패는 한 번에 수백 건이 날 수 있어 토큰 전문을 그대로
    // 찍으면 로그가 토큰으로 뒤덮인다. 어느 토큰인지 구분할 정도만 남긴다.
    private static final int TOKEN_LOG_PREFIX = 12;

    // 이 크기 미만의 배치는 "전부 INVALID_ARGUMENT"여도 페이로드 버그로 의심하지 않는다.
    // 토큰이 2~3개뿐이면 진짜 죽은 토큰만 우연히 모여 있을 수 있어 페이로드 버그와 구분되지 않는다.
    private static final int SUSPECTED_PAYLOAD_FAILURE_MIN_BATCH = 5;

    private final DeadTokenCleanupService deadTokenCleanupService;

    // 단일 토큰에 FCM 발송
    public boolean sendToToken(String token, String title, String body,
                               String notificationType, String alertId) {
        try {
            Message message = Message.builder()
                    .setToken(token)
                    .putData("title", title != null ? title : "")
                    .putData("body", body != null ? body : "")
                    .putData("notificationType", notificationType)
                    .putData("alertId", alertId != null ? alertId : "")
                    .setAndroidConfig(buildAndroidConfig(notificationType))
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("FCM 발송 성공 - messageId: {}", response);
            return true;

        } catch (FirebaseMessagingException e) {
            // 영구 무효 판정은 배치 경로와 같은 기준(isDeadTokenError)을 쓴다. 조건을 여기에
            // 따로 나열하면 한쪽만 고쳐져 두 경로의 판정이 갈라진다.
            MessagingErrorCode code = e.getMessagingErrorCode();
            if (isDeadTokenError(code)) {
                logDeadToken("FCM 발송", token, code);
                deadTokenCleanupService.cleanUp(List.of(token));
            } else {
                log.error("FCM 발송 실패 - token: {}, errorCode: {}, error: {}",
                        maskToken(token), code, e.getMessage());
            }
            return false;
        }
    }

    // 다중 토큰에 FCM 발송 (최대 500개)
    public BatchResponse sendToTokens(List<String> tokens, String title, String body,
                                      String notificationType, String alertId) {
        if (tokens.isEmpty()) return null;

        try {
            MulticastMessage message = MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .putData("title", title != null ? title : "")
                    .putData("body", body != null ? body : "")
                    .putData("notificationType", notificationType)
                    .putData("alertId", alertId != null ? alertId : "")
                    .setAndroidConfig(buildAndroidConfig(notificationType))
                    .build();

            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            log.info("FCM 다중 발송 - 성공: {}, 실패: {}",
                    response.getSuccessCount(), response.getFailureCount());

            logBatchFailures(tokens, response);

            // 배치가 통째로 INVALID_ARGUMENT 로 죽었다면 토큰이 아니라 페이로드를 의심한다.
            // 정리를 막지는 않는다 — isDeadTokenError 가 INVALID_ARGUMENT 를 삭제 대상에서
            // 아예 제외하므로 이 상황에서 지워질 토큰은 애초에 없다. 여기서는 원인만 짚어준다.
            if (isSuspectedPayloadFailure(tokens, response)) {
                log.error("FCM 배치가 전부 INVALID_ARGUMENT 로 실패 — 토큰이 아니라 메시지 페이로드 문제로 "
                        + "의심된다. 발송 코드/설정 변경을 확인할 것 - 토큰 수: {}", tokens.size());
            }
            deadTokenCleanupService.cleanUp(collectDeadTokens(tokens, response));
            return response;

        } catch (FirebaseMessagingException e) {
            log.error("FCM 다중 발송 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 배치에서 실패한 토큰을 <b>건별로</b> 남긴다.
     *
     * <p>기존에는 실패 개수만 찍혀서 "몇 개 실패했다"는 사실만 알 수 있었고, 어느 토큰이 왜
     * 실패했는지 추적할 수단이 없었다. 죽어서 정리될 토큰과 쿼터·네트워크 때문에 재시도해야 할
     * 토큰이 로그상 구분되지 않으면, 정리가 제대로 도는지도 확인할 수 없다.
     *
     * <p>토큰은 접두사만 남긴다. 배치 실패는 한 번에 수백 건이 날 수 있는 데다, 토큰 자체가
     * 해당 기기로 푸시를 보낼 수 있는 값이라 전문을 로그에 흘릴 이유가 없다.
     */
    private void logBatchFailures(List<String> tokens, BatchResponse response) {
        if (response.getFailureCount() == 0) return;

        List<SendResponse> responses = response.getResponses();
        if (responses == null || responses.size() != tokens.size()) return;

        for (int i = 0; i < tokens.size(); i++) {
            SendResponse sendResponse = responses.get(i);
            if (sendResponse.isSuccessful()) continue;

            FirebaseMessagingException exception = sendResponse.getException();
            MessagingErrorCode code = exception == null ? null : exception.getMessagingErrorCode();
            if (isDeadTokenError(code)) {
                logDeadToken("FCM 배치 발송", tokens.get(i), code);
            } else {
                log.error("FCM 배치 발송 실패 - token: {}, errorCode: {}, message: {}",
                        maskToken(tokens.get(i)), code,
                        exception == null ? "-" : exception.getMessage());
            }
        }
    }

    /**
     * 죽은 토큰으로 인한 발송 실패를 남긴다.
     *
     * <p><b>{@code error} 가 아니라 {@code warn} 인 것이 요점이다.</b> 토큰이 무효가 되는 것은
     * 브라우저 캐시 삭제·앱 재설치로 늘 일어나는 정상 현상이고, 이 코드가 곧바로 정리까지 하므로
     * 사람이 조치할 것이 없다. error 로 남기면 로그에서 진짜 장애를 골라내기 어려워진다.
     *
     * <p>반대로 쿼터 초과·서버 오류처럼 재시도 대상인 실패는 호출부에서 {@code error} 로 남긴다 —
     * 그쪽은 정말로 사람이 봐야 하는 신호다. 단건·배치 두 경로가 같은 기준으로 갈리도록
     * 이 메서드를 공유한다.
     */
    private void logDeadToken(String context, String token, MessagingErrorCode code) {
        log.warn("{} 실패(죽은 토큰, 정리 대상) - token: {}, errorCode: {}",
                context, maskToken(token), code);
    }

    /**
     * 배치 전멸이 <b>토큰이 아니라 메시지 페이로드 때문</b>으로 의심되는지.
     *
     * <p><b>이건 안전장치가 아니라 진단이다.</b> 원래는 정리를 막는 가드로 만들었는데,
     * {@link #isDeadTokenError} 가 {@code INVALID_ARGUMENT} 를 삭제 대상에서 아예 제외하게
     * 되면서 막을 삭제 자체가 없어졌다. 배치 단위 가드로는 부족했기 때문이다 — 회원 발송은
     * 기기가 1대면 단건 경로({@link #sendToToken})로 빠져 가드를 아예 타지 않고, 2~4대면
     * {@link #SUSPECTED_PAYLOAD_FAILURE_MIN_BATCH} 미만이라 걸리지 않아 회원 경로가 사실상
     * 무방비였다. 그래서 방어는 "모호한 코드로는 지우지 않는다"로 옮겼고, 이 메서드는
     * <b>원인을 짚어주는 역할만</b> 남았다.
     *
     * <p>그 역할은 여전히 값이 있다. {@code INVALID_ARGUMENT} 는 토큰 형식 오류만 뜻하지 않고
     * 메시지의 공유 필드(제목·본문·{@code data}·{@code AndroidConfig})가 잘못됐을 때도 같은
     * 코드로 돌아온다. 발송 코드나 설정을 바꾼 직후 배치가 통째로 죽으면, 이 판정이 없으면
     * 정체불명의 실패 로그 수십 줄만 남는다. 있으면 "토큰이 아니라 네가 방금 바꾼 페이로드가
     * 문제다"라고 한 줄로 알려준다.
     *
     * <p>판별의 핵심은 <b>하나라도 성공했는지</b>다. 성공한 토큰이 있으면 페이로드는 유효하므로
     * 나머지의 {@code INVALID_ARGUMENT} 는 진짜 토큰 문제다. 전부 실패했고 그 전부가
     * {@code INVALID_ARGUMENT} 일 때만 페이로드를 의심한다.
     *
     * <p>배치가 아주 작으면(1~2개) 진짜 깨진 토큰만 모여 있을 수도 있어 구분이 안 된다.
     * {@link #SUSPECTED_PAYLOAD_FAILURE_MIN_BATCH} 이상일 때만 의심하는 이유다. 진단 용도라
     * 임계값을 틀려도 잘못된 로그 한 줄이 나올 뿐, 데이터가 지워지지는 않는다.
     */
    static boolean isSuspectedPayloadFailure(List<String> tokens, BatchResponse response) {
        // 방어 조건은 전부 "근거 없으면 의심하지 않는다" 방향이다 — collectDeadTokens와 반대다.
        // 그쪽은 지우는 쪽이 위험해서 모르면 안 지우고, 여기는 잘못 짚은 진단이 사람을 엉뚱한
        // 곳으로 보내므로 모르면 아무 말도 하지 않는다.
        if (tokens == null || tokens.isEmpty() || response == null) {
            return false;
        }
        if (tokens.size() < SUSPECTED_PAYLOAD_FAILURE_MIN_BATCH) {
            return false;
        }

        List<SendResponse> responses = response.getResponses();
        if (responses == null || responses.size() != tokens.size()) {
            return false;
        }

        // 하나라도 성공하면 페이로드는 유효하다는 뜻이므로 즉시 의심을 접는다.
        // 나머지 실패가 전부 INVALID_ARGUMENT여도 진짜 토큰 문제일 뿐이다.
        for (SendResponse sendResponse : responses) {
            if (sendResponse.isSuccessful()) {
                return false;
            }
            FirebaseMessagingException exception = sendResponse.getException();
            MessagingErrorCode code = exception == null ? null : exception.getMessagingErrorCode();
            if (code != MessagingErrorCode.INVALID_ARGUMENT) {
                return false;
            }
        }
        return true;
    }

    // 토큰은 그 자체로 해당 기기에 푸시를 보낼 수 있는 값이라 로그에 전문을 남기지 않는다.
    private static String maskToken(String token) {
        if (token == null) return "null";
        return token.length() <= TOKEN_LOG_PREFIX ? token : token.substring(0, TOKEN_LOG_PREFIX) + "...";
    }

    /**
     * 배치 응답에서 <b>더 이상 살아있지 않은 토큰</b>만 골라낸다.
     *
     * <p><b>인덱스 매핑이 이 메서드의 전부이자 위험 지점이다.</b> Firebase 는
     * {@link BatchResponse#getResponses()} 의 i 번째가 요청 토큰의 i 번째에 대응한다고 보장한다.
     * 이 대응을 틀리면 <b>살아있는 토큰을 지우게 되어</b>, 정상 구독자에게 푸시가 영구히 끊긴다.
     * 실패한 응답만 세거나 순서를 재정렬하면 곧바로 그 사고가 나므로 단위 테스트로 못박는다.
     *
     * <p>실패했다고 전부 죽은 토큰은 아니다. 쿼터 초과·일시적 서버 오류(`UNAVAILABLE`,
     * `INTERNAL`)는 재시도하면 되는 상태라 지우면 안 된다. {@link #isDeadTokenError} 가
     * 판정하는 {@code UNREGISTERED} 하나만 삭제 대상이다.
     *
     * <p>방어 계약: {@code tokens}/{@code response} 가 null 이거나, 토큰 수와 응답 수가
     * 어긋나면 <b>아무것도 반환하지 않는다</b>. 지울 것을 놓치는 쪽이 잘못 지우는 쪽보다
     * 안전하기 때문이다 — 전자는 다음 발송에서 다시 걸리지만, 후자는 정상 구독자의 구독이
     * 영구히 끊겨 복구할 수 없다.
     */
    static List<String> collectDeadTokens(List<String> tokens, BatchResponse response) {
        if (tokens == null || tokens.isEmpty() || response == null) {
            return List.of();
        }

        List<SendResponse> responses = response.getResponses();
        // 토큰 수와 응답 수가 어긋나면 인덱스 대응을 신뢰할 수 없다 — 잘못 지워 복구 불가능한
        // 사고(살아있는 토큰 삭제)를 막기 위해 아무것도 지우지 않는다.
        // 다만 이 불일치 자체가 SDK 계약 위반이거나 호출부 버그를 뜻하는 이상 신호이므로,
        // 조용히 넘기지 않고 로그를 남긴다 (정리 작업이 매번 아무것도 못 지우고 있는 상태를
        // 알아챌 수 있는 유일한 단서다).
        if (responses == null || responses.size() != tokens.size()) {
            log.warn("FCM 토큰/응답 수 불일치로 죽은 토큰 판별을 건너뜀 - 토큰: {}, 응답: {}",
                    tokens.size(), responses == null ? "null" : responses.size());
            return List.of();
        }

        List<String> deadTokens = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            SendResponse sendResponse = responses.get(i);
            if (sendResponse.isSuccessful()) {
                continue;
            }
            FirebaseMessagingException exception = sendResponse.getException();
            if (exception != null && isDeadTokenError(exception.getMessagingErrorCode())) {
                deadTokens.add(tokens.get(i));
            }
        }
        return deadTokens;
    }

    /**
     * 이 오류 코드가 "토큰이 영구히 무효"를 뜻하는지.
     *
     * <ul>
     *   <li>{@code UNREGISTERED} — 앱/브라우저가 등록 해제됨. 캐시 삭제·재설치로 토큰이 갱신되면 옛 토큰이 이 상태가 된다.
     *       이 상태만은 삭제 대상으로 판정한다.</li>
     * </ul>
     *
     * <p>{@code INVALID_ARGUMENT} 는 삭제 대상에서 <b>제외</b>한다. 토큰 형식 오류만 뜻하지 않고,
     * Firebase 는 메시지 페이로드(제목·본문·{@code data}·{@code AndroidConfig})가 잘못됐을 때도
     * 같은 코드를 돌려준다. 이 둘을 구분할 방법이 없어 그대로 삭제하면, 발송 코드/설정 변경으로
     * 페이로드가 깨졌을 때 멀쩡한 구독자를 전량 삭제하게 되고 되돌릴 수 없다(사용자가 알림 권한을
     * 다시 허용해야 함). 배치 단위 가드({@link #isSuspectedPayloadFailure})로 막으려 했으나 회원
     * 기기가 1~4대인 실제 호출 경로(단건 발송·소규모 배치)는 그 가드가 걸리지 않아 무방비였다.
     * 대가는 진짜로 형식이 깨진 토큰이 자동 정리되지 않는 것인데, 드물고 계속 실패하며 error
     * 로그로 남으므로 감수한다.
     *
     * <p>나머지(쿼터·네트워크·서버 오류)는 일시적이므로 <b>삭제하지 않는다</b>.
     */
    static boolean isDeadTokenError(MessagingErrorCode code) {
        return code == MessagingErrorCode.UNREGISTERED;
    }

    // Android 설정 (ALARM: 높은 우선순위)
    private AndroidConfig buildAndroidConfig(String notificationType) {
        AndroidConfig.Builder builder = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH);

        if ("ALARM".equals(notificationType)) {
            builder.setNotification(
                    AndroidNotification.builder()
                            .setChannelId("disaster_alarm")
                            .setSound("default")
                            .setVibrateTimingsInMillis(new long[]{0, 200, 100, 200})
                            .setPriority(AndroidNotification.Priority.MAX)
                            .build()
            );
        } else {
            builder.setNotification(
                    AndroidNotification.builder()
                            .setChannelId("disaster_push")
                            .setSound("default")
                            .build()
            );
        }
        return builder.build();
    }
}