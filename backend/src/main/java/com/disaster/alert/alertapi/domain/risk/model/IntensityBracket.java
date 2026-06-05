package com.disaster.alert.alertapi.domain.risk.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 강도 구간 → multiplier 매핑.
 *
 * <p>본문에서 추출한 강도 수치(규모 5.2, 시간당 50mm 등)가
 * [min_value, max_value) 구간에 들어가면 해당 multiplier 를 weight 에 곱한다.
 * 매핑이 없으면 multiplier=1.0 (강도 미반영).
 */
@Entity
@Table(name = "intensity_bracket")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IntensityBracket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "disaster_type", length = 50, nullable = false)
    private String disasterType;

    @Column(name = "min_value", nullable = false)
    private double minValue;

    @Column(name = "max_value", nullable = false)
    private double maxValue;

    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "multiplier", nullable = false)
    private double multiplier;

    @Column(name = "label", length = 50)
    private String label;
}
