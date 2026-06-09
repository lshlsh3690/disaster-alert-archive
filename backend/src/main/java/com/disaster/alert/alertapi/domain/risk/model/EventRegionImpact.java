package com.disaster.alert.alertapi.domain.risk.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 이벤트 → 법정동 영향.
 *
 * <p>impact_score 는 시간 감쇠 적용 <b>전</b> baseScore (weight × intensity × severity).
 * 감쇠는 읽을 때 disaster_events.last_alert_at 기준으로 계산한다.
 *
 * <p>event 단위 기록이라 같은 사건의 알림이 N건이어도 영향은 1번만 — 이중 카운팅 방지.
 *
 * <p>주: 실제 upsert/조회는 {@code EventRegionImpactRepository} 의 네이티브 쿼리로 한다.
 * 이 엔티티는 JPA 매핑/스키마 검증용.
 */
@Entity
@Table(name = "event_region_impact")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EventRegionImpact {

    @EmbeddedId
    private EventRegionImpactId id;

    @Column(name = "impact_score", nullable = false)
    private double impactScore;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
