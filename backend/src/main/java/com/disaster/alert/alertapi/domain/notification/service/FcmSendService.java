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
     * 사람이 볼 필요가 없다. 이 레포는 {@code log.error} 를 Sentry 이벤트로 승격시키므로
     * error 로 남기면 정상 동작이 매번 이슈로 올라온다.
     *
     * <p>반대로 쿼터 초과·서버 오류처럼 재시도 대상인 실패는 호출부에서 {@code error} 로 남긴다 —
     * 그쪽은 정말로 사람이 봐야 하는 신호다. 단건·배치 두 경로가 같은 기준으로 갈리도록
     * 이 메서드를 공유한다.
     */
    private void logDeadToken(String context, String token, MessagingErrorCode code) {
        log.warn("{} 실패(죽은 토큰, 정리 대상) - token: {}, errorCode: {}",
                context, maskToken(token), code);
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
     * 판정하는 두 코드만 삭제 대상이다.
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
     *   <li>{@code UNREGISTERED} — 앱/브라우저가 등록 해제됨. 캐시 삭제·재설치로 토큰이 갱신되면 옛 토큰이 이 상태가 된다</li>
     *   <li>{@code INVALID_ARGUMENT} — 토큰 형식 자체가 잘못됨</li>
     * </ul>
     * 나머지(쿼터·네트워크·서버 오류)는 일시적이므로 <b>삭제하지 않는다</b>.
     */
    static boolean isDeadTokenError(MessagingErrorCode code) {
        return code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT;
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