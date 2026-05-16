package com.disaster.alert.alertapi.domain.notification.dto;

import com.disaster.alert.alertapi.domain.notification.model.NotificationPreference;
import com.disaster.alert.alertapi.domain.notification.model.NotificationType;
import jakarta.validation.constraints.NotNull;

public class NotificationPreferenceDtos {

    public record Response(
            NotificationType notificationType,
            int minRiskScore
    ) {
        public static Response from(NotificationPreference preference) {
            return new Response(
                    preference.getNotificationType(),
                    preference.getMinRiskScore()
            );
        }

        // 기본값 (설정이 없는 신규 유저용)
        public static Response defaultValue() {
            return new Response(NotificationType.PUSH, 0);
        }
    }

    public record UpdateRequest(
            @NotNull(message = "notificationType은 필수입니다.")
            NotificationType notificationType
    ) {
    }
}