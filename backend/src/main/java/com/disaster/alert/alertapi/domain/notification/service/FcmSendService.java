package com.disaster.alert.alertapi.domain.notification.service;

import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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