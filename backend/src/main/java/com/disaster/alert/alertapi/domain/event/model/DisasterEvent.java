package com.disaster.alert.alertapi.domain.event.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 재난 이벤트 — 시간 흐름에 따라 N개 재난문자가 누적된 사건 단위.
 *
 * <p>클러스터링 규칙: 후보 검색(7일 윈도우 + 지역 교집합) 안에서 코사인 유사도 최대 후보가
 * 임계값을 넘으면 기존 이벤트에 머지. 못 넘으면 신규 이벤트.
 *
 * <p>대표 정보({@link #primaryDisasterType}, {@link #primaryRegionCode}, {@link #primaryRegionName})
 * 는 모두 첫 알림 기준 캐시. 화면 표시용 제목({@link #eventTitle}) 자동 생성.
 *
 * <p>상태머신(status, ACTIVE/MONITORING/RESOLVED) 컬럼은 다음 PR. 현 시점에서는
 * {@link #lastAlertAt} 으로 후보 검색 윈도우 + 상태 추론 모두 충당.
 */
@Entity
@Table(name = "disaster_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class DisasterEvent {

    private static final DateTimeFormatter TITLE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_title", nullable = false, length = 200)
    private String eventTitle;

    @Column(name = "primary_disaster_type", length = 50)
    private String primaryDisasterType;

    @Column(name = "primary_region_code", length = 20)
    private String primaryRegionCode;

    @Column(name = "primary_region_name", length = 100)
    private String primaryRegionName;

    @Column(name = "first_alert_at", nullable = false)
    private LocalDateTime firstAlertAt;

    @Column(name = "last_alert_at", nullable = false)
    private LocalDateTime lastAlertAt;

    @Column(name = "alert_count", nullable = false)
    private int alertCount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 첫 알림 기준 신규 이벤트 생성용 팩토리.
     * 제목은 {@code "{지역명} {유형} ({yyyy-MM-dd})"} 형태로 자동 생성.
     */
    public static DisasterEvent createFromFirstAlert(
            String disasterType,
            String regionCode,
            String regionName,
            LocalDateTime alertAt
    ) {
        String safeType = disasterType == null ? "UNKNOWN" : disasterType;
        String safeRegion = regionName == null ? "지역미상" : regionName;
        String title = String.format("%s %s (%s)", safeRegion, safeType, alertAt.format(TITLE_DATE));

        return DisasterEvent.builder()
                .eventTitle(title)
                .primaryDisasterType(safeType)
                .primaryRegionCode(regionCode)
                .primaryRegionName(safeRegion)
                .firstAlertAt(alertAt)
                .lastAlertAt(alertAt)
                .alertCount(1)
                .build();
    }

    /**
     * 기존 이벤트에 새 알림이 머지될 때 호출.
     * lastAlertAt 은 단조 증가 가정이지만, 백필에서 순서가 어긋날 수 있으므로 max() 로 보호.
     */
    public void recordMergedAlert(LocalDateTime alertAt) {
        if (alertAt != null && alertAt.isAfter(this.lastAlertAt)) {
            this.lastAlertAt = alertAt;
        }
        this.alertCount++;
    }
}
