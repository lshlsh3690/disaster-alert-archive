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

    /** effective 위험도 — 이웃에서 번져온 값까지 결합한 최종 점수(FE 노출). */
    @Column(name = "risk_score", nullable = false)
    private double riskScore;

    /** effective 점수의 진원 이벤트(이웃에서 번졌으면 그 이웃의 top event). 설명용. */
    @Column(name = "top_event_id")
    private Long topEventId;

    /** source 위험도 — 자기 시군구 직접 impact 만(전파 전). 확산 계산의 입력. */
    @Column(name = "source_score", nullable = false)
    private double sourceScore;

    /** source 점수의 top 이벤트(자기 시군구 기준). */
    @Column(name = "source_top_event_id")
    private Long sourceTopEventId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
