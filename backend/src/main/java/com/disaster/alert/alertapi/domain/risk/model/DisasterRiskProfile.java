package com.disaster.alert.alertapi.domain.risk.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 재난 유형별 위험 프로파일.
 *
 * <p>35종은 V32 seed 로 미리 채움. 35종에 없는 유형(예: 동물탈출, 화학사고 변종)은
 * {@link com.disaster.alert.alertapi.domain.risk.service.LlmRiskProfiler} 가
 * 런타임에 생성하여 저장(is_llm_generated=true)하고, 다음부터 캐시처럼 재사용한다.
 */
@Entity
@Table(name = "disaster_risk_profile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class DisasterRiskProfile {

    @Id
    @Column(name = "disaster_type", length = 50)
    private String disasterType;

    @Column(name = "base_weight", nullable = false)
    private double baseWeight;

    @Column(name = "half_life_hours", nullable = false)
    private int halfLifeHours;

    @Column(name = "is_llm_generated", nullable = false)
    private boolean llmGenerated;

    @Column(name = "operator_confirmed", nullable = false)
    private boolean operatorConfirmed;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** LLM 이 생성한 신규 유형 프로파일 생성용 팩토리. */
    public static DisasterRiskProfile ofLlm(String disasterType, double baseWeight, int halfLifeHours) {
        return DisasterRiskProfile.builder()
                .disasterType(disasterType)
                .baseWeight(baseWeight)
                .halfLifeHours(halfLifeHours)
                .llmGenerated(true)
                .operatorConfirmed(false)
                .build();
    }

    /** 운영자 보정. */
    public void applyOperatorOverride(double baseWeight, int halfLifeHours) {
        this.baseWeight = baseWeight;
        this.halfLifeHours = halfLifeHours;
        this.operatorConfirmed = true;
    }
}
