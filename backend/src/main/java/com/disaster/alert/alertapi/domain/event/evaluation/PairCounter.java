package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelCsv.Label;
import com.disaster.alert.alertapi.domain.event.evaluation.PairCountResult.WrongKind;
import com.disaster.alert.alertapi.domain.event.evaluation.PairCountResult.WrongPair;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * (라벨, 배정 여부, 같은 이벤트 여부) 관측을 세어 두 비율과 틀린 쌍 목록을 만든다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PairCounter {

    /**
     * @param assigned  두 알림 모두 이벤트에 들어 있으면 true
     * @param sameEvent 같은 이벤트면 true (assigned 가 false 면 의미 없음)
     */
    public record Observation(long alertA, long alertB, Label label, boolean assigned, boolean sameEvent) {
    }

    public static PairCountResult count(List<Observation> observations) {
        int correct = 0;
        int missed = 0;
        int over = 0;
        int unsure = 0;
        int unassigned = 0;
        List<WrongPair> wrong = new ArrayList<>();

        for (Observation o : observations) {
            // 1) UNSURE 를 먼저 거른다 — 모르겠음+미배정은 모르겠음으로만 센다(이중 집계 방지)
            if (o.label() == Label.UNSURE) {
                unsure++;
                continue;
            }
            // 2) 미배정 쌍은 정답을 가릴 수 없어 비율에서 빼고 건수만 센다
            if (!o.assigned()) {
                unassigned++;
                continue;
            }
            if (o.label() == Label.SAME) {
                // 3) 같은 사건: 같은 이벤트면 맞게 묶음, 아니면 놓침
                if (o.sameEvent()) {
                    correct++;
                } else {
                    missed++;
                    wrong.add(new WrongPair(o.alertA(), o.alertB(), WrongKind.MISSED));
                }
            } else if (o.sameEvent()) {
                // 4) 다른 사건인데 같은 이벤트면 잘못 묶음 (다른 이벤트는 어디에도 안 센다)
                over++;
                wrong.add(new WrongPair(o.alertA(), o.alertB(), WrongKind.OVER_MERGED));
            }
        }
        return new PairCountResult(correct, missed, over, unsure, unassigned,
                rate(missed, correct + missed), rate(over, correct + over), wrong);
    }

    // 분모 0 이면 0% 가 아니라 empty — 0% 는 "아주 좋음" 으로 오해된다
    private static OptionalDouble rate(int numerator, int denominator) {
        return denominator == 0 ? OptionalDouble.empty() : OptionalDouble.of(numerator * 100.0 / denominator);
    }
}
