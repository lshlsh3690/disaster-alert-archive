package com.disaster.alert.alertapi.domain.event.judge;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson;

import java.util.List;

/**
 * 판사 일치율을 "쌍별 판사 판정 x 현재 사람 라벨"로 계산한다.
 *
 * <p>판정은 측정 시점에 쌍마다 저장해 두고, 사람 라벨은 계산할 때의 현재 값을 쓴다. 그래서 라벨을 고치면 같은 판정으로
 * 일치율을 다시 계산할 수 있다. JPA·Spring 을 쓰지 않는 순수 클래스다(헌법 VI).
 */
// Red 단계 뼈대 — 구현은 Green 에서
public final class JudgeAgreementCalculator {

    private JudgeAgreementCalculator() {
    }

    /** 측정의 쌍 하나에 판사가 낸 판정. 알림 순서는 상관없다. */
    public record PairVerdict(long alertA, long alertB, RecordedVerdict verdict) {
    }

    /** 현재 사람 라벨 하나. 알림 순서는 상관없다(evaluation_pair_label 은 원래 순서로 저장됨). */
    public record PairLabel(long alertA, long alertB, LabelJson.Label label) {
    }

    public record Result(int sameNum, int sameDen, int diffNum, int diffDen,
                         int judgeUnsure, int formatFailures, int callFailures,
                         int unmeasuredPairs, int unlabeledVerdicts) {
    }

    public static Result calculate(List<PairVerdict> verdicts, List<PairLabel> labels) {
        return new Result(0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
