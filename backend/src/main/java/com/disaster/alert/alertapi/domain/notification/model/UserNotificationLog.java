package com.disaster.alert.alertapi.domain.notification.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_notification_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class UserNotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "alert_id", nullable = false)
    private Long alertId;

    @Column(name = "status", nullable = false)
    private String status; // SENT / FAILED

    @Column(name = "notification_type", nullable = false)
    private String notificationType;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public UserNotificationLog(Long memberId, Long alertId,
                               String status, String notificationType) {
        this.memberId = memberId;
        this.alertId = alertId;
        this.status = status;
        this.notificationType = notificationType;
    }
}