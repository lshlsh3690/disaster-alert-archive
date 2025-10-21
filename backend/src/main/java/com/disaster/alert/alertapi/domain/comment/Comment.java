package com.disaster.alert.alertapi.domain.comment;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.disaster.alert.alertapi.domain.member.model.Member;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.useralert.model.UserDisasterAlert;

@Entity
@Table(
        name = "comment",
        indexes = {
                @Index(name = "idx_comment_disaster_alert_id", columnList = "disaster_alert_id"),
                @Index(name = "idx_comment_user_disaster_alert_id", columnList = "user_disaster_alert_id"),
                @Index(name = "idx_comment_member_id", columnList = "member_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String content;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Column(columnDefinition = "boolean default false")
    private boolean isDeleted = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disaster_alert_id")
    private DisasterAlert disasterAlert;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_disaster_alert_id")
    private UserDisasterAlert userDisasterAlert;
}
