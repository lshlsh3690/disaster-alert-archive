package com.disaster.alert.alertapi.domain.risk.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 지역(시군구)별 현재 위험도.
 *
 * <p>법정동 단위 event_region_impact 를 시군구(코드 앞 5자리)로 집계 + 시간 감쇠 적용 후
 * MAX 결합한 0~1 정규화 점수. 스케줄러가 주기적으로 갱신한다.
 *
 * <p>top_event_id 는 가장 큰 기여 이벤트(설명용). 상세 화면의 contributingEvents 는
 * event_region_impact 를 active event 와 조인하여 별도 조회.
 */
@Entity
@Table(name = "region_risk_index")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionRiskIndex {

    @Id
    @Column(name = "region_code", length = 20)
    private String regionCode;

    @Column(name = "risk_score", nullable = false)
    private double riskScore;

    @Column(name = "top_event_id")
    private Long topEventId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
