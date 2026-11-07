package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.ReportedPair.Kind;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 이전 리포트의 틀린 쌍과 이번 틀린 쌍을 비교한다. 쌍은 순서 무관으로 같다고 본다.
 */
// Red 단계 뼈대 — 구현은 Green 에서
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReportDiff {

    /** 고쳐진 쌍(이전에만 있음), 새로 틀린 쌍(이번에만 있음), 둘 다 틀렸지만 종류가 바뀐 쌍. */
    public record Result(List<ReportedPair> fixed, List<ReportedPair> newlyWrong, List<KindChange> kindChanged) {
    }

    public record KindChange(long alertA, long alertB, Kind before, Kind after) {
    }

    public static Result compare(List<ReportedPair> previous, List<ReportedPair> current) {
        return new Result(List.of(), List.of(), List.of());
    }
}
