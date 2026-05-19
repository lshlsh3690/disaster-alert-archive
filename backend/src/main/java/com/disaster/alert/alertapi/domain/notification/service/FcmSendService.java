package com.disaster.alert.alertapi.domain.notification.service;

import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class FcmSendService {

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
                    .setWebpushConfig(buildWebpushConfig(notificationType))
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
                    .setWebpushConfig(buildWebpushConfig(notificationType))
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

    // WebPush 설정
    private WebpushConfig buildWebpushConfig(String notificationType) {
        WebpushNotification.Builder notifBuilder = WebpushNotification.builder()
                .setIcon("/icons/icon-192x192.png")
                .setBadge("/icons/icon-72x72.png");

        if ("ALARM".equals(notificationType)) {
            notifBuilder.setRequireInteraction(true);
        }

        return WebpushConfig.builder()
                .setNotification(notifBuilder.build())
                .build();
    }
}