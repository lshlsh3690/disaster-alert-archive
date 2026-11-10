package com.disaster.alert.alertapi.domain.event.evaluation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Optional;

/**
 * 튜닝 전후 비교에서 "평가 대상이 줄어서 좋아 보이는" 일을 막는 판정.
 * 이벤트 미배정이 기준선보다 늘었거나, 이전 틀린 쌍이 이번에 평가 대상에서 빠졌다면 개선으로 인정하지 않는다.
 */
// Red 단계 뼈대 — 구현은 Green 에서
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EvaluationTargetCheck {

    /**
     * @return 비어 있으면 통과, 값이 있으면 실패 사유
     */
    public static Optional<String> shrinkReason(int baselineUnassigned, int currentUnassigned, ReportDiff.Result diff) {
        return Optional.empty();
    }
}
