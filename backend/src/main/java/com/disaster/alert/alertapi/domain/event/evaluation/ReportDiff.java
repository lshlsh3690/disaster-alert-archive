package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.ReportedPair.Kind;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 이전 리포트의 틀린 쌍과 이번 틀린 쌍을 비교한다. 쌍은 순서 무관으로 같다고 본다.
 *
 * <p>같은 라벨 셋을 반복해 돌리므로 비율 변화보다 "어느 쌍이 고쳐졌고 어느 쌍이 새로 틀렸는지" 가
 * 튜닝 효과를 더 민감하게 보여 준다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReportDiff {

    /** 고쳐진 쌍(이전에만 있음), 새로 틀린 쌍(이번에만 있음), 둘 다 틀렸지만 종류가 바뀐 쌍. */
    public record Result(List<ReportedPair> fixed, List<ReportedPair> newlyWrong, List<KindChange> kindChanged) {
    }

    public record KindChange(long alertA, long alertB, Kind before, Kind after) {
    }

    /**
     * @return fixed 는 이전 리포트의 표기, newlyWrong·kindChanged 는 이번 표기를 쓴다. 각각 입력 순서를 유지한다
     */
    public static Result compare(List<ReportedPair> previous, List<ReportedPair> current) {
        Map<PairKey, ReportedPair> before = index(previous);
        Map<PairKey, ReportedPair> after = index(current);

        List<ReportedPair> fixed = new ArrayList<>();
        List<ReportedPair> newlyWrong = new ArrayList<>();
        List<KindChange> kindChanged = new ArrayList<>();

        // 1) 이전에만 있으면 고쳐진 쌍
        before.forEach((key, pair) -> {
            if (!after.containsKey(key)) {
                fixed.add(pair);
            }
        });
        // 2) 이번에만 있으면 새로 틀린 쌍, 둘 다 있으면 종류만 비교한다
        after.forEach((key, pair) -> {
            ReportedPair old = before.get(key);
            if (old == null) {
                newlyWrong.add(pair);
            } else if (old.kind() != Kind.UNKNOWN && old.kind() != pair.kind()) {
                // 이전이 UNKNOWN(옛 6칸 리포트)이면 바뀜으로 세지 않는다 — 안 그러면 옛 리포트를 기준선으로 쓸 때
                // 모든 쌍이 "종류 바뀜" 으로 잡혀 진짜 변화가 묻힌다
                kindChanged.add(new KindChange(pair.alertA(), pair.alertB(), old.kind(), pair.kind()));
            }
        });
        return new Result(fixed, newlyWrong, kindChanged);
    }

    private static Map<PairKey, ReportedPair> index(List<ReportedPair> pairs) {
        Map<PairKey, ReportedPair> map = new LinkedHashMap<>();
        for (ReportedPair p : pairs) {
            // 같은 쌍이 두 번 있으면 먼저 나온 것을 유지한다
            map.putIfAbsent(PairKey.of(p.alertA(), p.alertB()), p);
        }
        return map;
    }

    /** (a,b) 와 (b,a) 를 같은 키로 만들려고 작은 id 를 앞에 둔다. ReportFormatter 도 사실 행을 찾을 때 쓴다. */
    record PairKey(long low, long high) {
        static PairKey of(long a, long b) {
            return new PairKey(Math.min(a, b), Math.max(a, b));
        }
    }
}
