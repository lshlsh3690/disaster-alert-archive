package com.disaster.alert.alertapi.domain.notification.dto;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.notification.model.UserNotificationLog;

import java.time.LocalDateTime;
import java.util.List;

public class NotificationLogDtos {

    public record ListItem(
            Long id,
            Long alertId,
            String message,
            String disasterType,
            String originalRegion,
            String emergencyLevel,
            boolean isRead,
            LocalDateTime sentAt
    ) {
        public static ListItem from(UserNotificationLog log) {
            DisasterAlert alert = log.getDisasterAlert();
            return new ListItem(
                    log.getId(),
                    log.getAlertId(),
                    alert != null ? alert.getMessage() : null,
                    alert != null ? alert.getDisasterType() : null,
                    alert != null ? alert.getOriginalRegion() : null,
                    alert != null && alert.getEmergencyLevel() != null
                            ? alert.getEmergencyLevel().getDescription()
                            : null,
                    log.isRead(),
                    log.getCreatedAt()
            );
        }
    }

    public record PageResponse(
            List<ListItem> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            long unreadCount
    ) {}
}
