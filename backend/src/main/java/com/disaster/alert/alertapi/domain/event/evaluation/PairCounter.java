package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelCsv.Label;

import java.util.List;
import java.util.OptionalDouble;

/**
 * (라벨, 배정 여부, 같은 이벤트 여부) 관측을 세어 두 비율과 틀린 쌍 목록을 만든다.
 */
// Red 단계 뼈대 — 구현은 Green 에서
public final class PairCounter {

    /**
     * @param assigned  두 알림 모두 이벤트에 들어 있으면 true
     * @param sameEvent 같은 이벤트면 true (assigned 가 false 면 의미 없음)
     */
    public record Observation(long alertA, long alertB, Label label, boolean assigned, boolean sameEvent) {
    }

    public enum WrongKind { MISSED, OVER_MERGED }

    public record WrongPair(long alertA, long alertB, WrongKind kind) {
    }

    /**
     * 비율은 퍼센트 값(0~100). 분모가 0 이면 empty.
     */
    public record Result(int correctlyMerged, int missed, int overMerged,
                         int excludedUnsure, int excludedUnassigned,
                         OptionalDouble underMergeRate, OptionalDouble overMergeRate,
                         List<WrongPair> wrongPairs) {
    }

    private PairCounter() {
    }

    public static Result count(List<Observation> observations) {
        return new Result(0, 0, 0, 0, 0, OptionalDouble.empty(), OptionalDouble.empty(), List.of());
    }
}
