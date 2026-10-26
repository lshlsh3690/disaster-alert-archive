package com.disaster.alert.alertapi.domain.event.evaluation;

import java.util.ArrayList;
import java.util.List;

/**
 * 판사 답이 사람 라벨과 얼마나 일치하는지 센다(판사 신뢰도). 순수 로직.
 * 쌍은 SAME, 분류는 INCIDENT 를 positive 로 두고, 사람이 positive 라 한 쪽과 아니라고 한 쪽을
 * 각자의 분모로 따로 센다 — 한쪽 오류만 많은 판사가 평균에 가려지지 않게 하기 위해서다.
 */
public final class JudgeAgreement {

    private JudgeAgreement() {
    }

    /** 한 항목의 사람 라벨과 판사 답. judge 가 null 이면 판정 불가. 사람이 "모르겠음" 인 항목은 넘기기 전에 뺀다. */
    public record Item<T>(long id, T human, T judge) {
    }

    /**
     * @param positives      사람이 positive 라 한 항목 수 (판정 불가 제외)
     * @param positiveMissed 그중 판사가 다른 답을 낸 id
     * @param negatives      사람이 positive 가 아니라 한 항목 수 (판정 불가 제외)
     * @param negativeMissed 그중 판사가 다른 답을 낸 id
     * @param undecided      판사가 판정 불가(null)인 id — 두 분모 어디에도 넣지 않는다
     */
    public record Result(int positives, List<Long> positiveMissed,
                         int negatives, List<Long> negativeMissed,
                         List<Long> undecided) {
    }

    public static <T> Result score(List<Item<T>> items, T positive) {
        int positives = 0;
        int negatives = 0;
        List<Long> positiveMissed = new ArrayList<>();
        List<Long> negativeMissed = new ArrayList<>();
        List<Long> undecided = new ArrayList<>();
        for (Item<T> it : items) {
            // 1) 판정 불가는 어느 분모에도 넣지 않는다 — 넣으면 "판사가 틀렸다"로 읽히고, 빼고 안 세면 조용히 사라진다
            if (it.judge() == null) {
                undecided.add(it.id());
                continue;
            }
            // 2) 사람이 positive 라 한 쪽: 판사가 다른 답이면 놓친 것
            if (it.human() == positive) {
                positives++;
                if (it.judge() != positive) {
                    positiveMissed.add(it.id());
                }
            } else {
                // 3) 그 밖은 negative 쪽: 판사가 positive 라 답하면 놓친 것
                negatives++;
                if (it.judge() == positive) {
                    negativeMissed.add(it.id());
                }
            }
        }
        return new Result(positives, positiveMissed, negatives, negativeMissed, undecided);
    }
}
