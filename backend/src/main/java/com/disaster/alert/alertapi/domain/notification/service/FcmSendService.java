package com.disaster.alert.alertapi.domain.notification.service;

import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FcmSendService {

    // 웹푸시는 data-only 메시지로만 발송한다 (webpush.notification 페이로드를 넣지 않음).
    // notification 페이로드가 있으면 Firebase JS SDK가 서비스워커에서 알림을 자동으로 한 번 더 표시하여,
    // onBackgroundMessage가 띄우는 알림과 합쳐져 알림이 2번(두 번째는 제목·본문 없는 빈 알림) 표시된다.
    // 실제 표시는 firebase-messaging-sw.js의 onBackgroundMessage에서 전담한다.

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
            log.error("FCM 발송 실패 - token: {}, error: {}", token, e.getMessage());

            // 만료된 토큰 처리
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED ||
                    e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                log.warn("만료된 FCM 토큰: {}", token);
                return false;
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
            return response;

        } catch (FirebaseMessagingException e) {
            log.error("FCM 다중 발송 실패: {}", e.getMessage());
            return null;
        }
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