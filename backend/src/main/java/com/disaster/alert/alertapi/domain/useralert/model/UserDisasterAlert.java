package com.disaster.alert.alertapi.domain.useralert.model;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_disaster_alert")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
public class UserDisasterAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    @OneToMany(mappedBy = "userDisasterAlert", cascade = CascadeType.PERSIST, orphanRemoval = true)
    private List<UserDisasterAlertRegion> regions = new ArrayList<>();

    @Column(nullable = false)
    private Long createdById; // Member ID

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 300)
    private String message;

    @Column(length = 50)
    private String disasterType; // e.g., "홍수", "태풍"

    @Enumerated(EnumType.STRING)
    private DisasterLevel disasterLevel;

    @Column(nullable = false)
    private LocalDateTime occurredAt; // 발생 시각

    @Column(nullable = false, updatable = false)
    @CreatedDate
    private LocalDateTime createdAt; // 알림 생성 시각

    @LastModifiedDate
    private LocalDateTime modifiedAt;
}
