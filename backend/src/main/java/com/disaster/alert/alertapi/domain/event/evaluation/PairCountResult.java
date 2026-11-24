package com.disaster.alert.alertapi.domain.event.evaluation;

import java.util.List;
import java.util.OptionalDouble;

/**
 * PairCounter 가 세어 낸 결과. ReportFormatter 가 이 값을 읽어 리포트를 만든다.
 * 비율은 퍼센트 값(0~100)이고, 분모가 0 이면 empty 다.
 */
public record PairCountResult(int correctlyMerged, int missed, int overMerged, int correctlySeparated,
                              int excludedUnsure, int excludedUnassigned,
                              OptionalDouble underMergeRate, OptionalDouble overMergeRate,
                              List<WrongPair> wrongPairs) {

    // Red 단계 뼈대 — 구현은 Green 에서. 기존 여섯 집계만으로 맞게 나눔을 복원하는 진입점이다.
    public static PairCountResult fromDailyCounts(int pairCount, int correctlyMerged, int missed,
                                                   int overMerged, int unsureCount, int formatFailureCount) {
        return new PairCountResult(0, 0, 0, 0, 0, 0,
                OptionalDouble.empty(), OptionalDouble.empty(), List.of());
    }

    // Red 단계 뼈대 — 구현은 Green 에서. 다른 사건 쌍을 분모로 쓰는 비율이다.
    public OptionalDouble separation() {
        return OptionalDouble.empty();
    }

    // Red 단계 뼈대 — 구현은 Green 에서. 두 클래스의 정답률을 같은 비중으로 읽는 대표 지표다.
    public OptionalDouble balancedAccuracy() {
        return OptionalDouble.empty();
    }

    // Red 단계 뼈대 — 구현은 Green 에서. 과병합과 맞게 나눔이 속하는 전체 다른 사건 쌍이다.
    public int differentEventPairs() {
        return 0;
    }

    /**
     * 맞게 묶은 비율(%) = 묶은 쌍 중 맞게 묶은 비율 = 맞게 묶음 ÷ (맞게 묶음 + 과병합). 과병합율의 반대편 수치다.
     * 묶은 쌍이 하나도 없으면 비율을 정할 수 없어 empty 다(0% 로 쓰면 "전부 틀림" 으로 읽힌다).
     */
    public OptionalDouble purity() {
        return ratio(correctlyMerged, mergedPairs());
    }

    /**
     * 빠짐없이 묶은 비율(%) = 같은 사건 쌍 중 실제로 묶은 비율 = 맞게 묶음 ÷ (맞게 묶음 + 과소병합). 과소병합율의 반대편 수치다.
     * 같은 사건 쌍이 하나도 없으면 empty.
     */
    public OptionalDouble completeness() {
        return ratio(correctlyMerged, sameEventPairs());
    }

    /**
     * 점수(0~100) = 맞게 묶은 비율과 빠짐없이 묶은 비율의 조화평균 = 2C ÷ (2C + 과소병합 + 과병합).
     * 반올림된 두 비율을 다시 조합하지 않고 개수로 바로 계산해 반올림 오차가 겹치지 않게 한다.
     * 조화평균을 쓰는 이유: 전부 묶어 맞게 묶은 비율만 높이거나 거의 안 묶어 빠짐없이 묶은 비율만 높여도, 낮은 쪽에 끌려 점수가 내려간다.
     * 분모(2C + 과소병합 + 과병합)가 0 이면 평가할 쌍이 없다는 뜻이라 empty.
     */
    public OptionalDouble score() {
        return ratio(2 * correctlyMerged, mergedPairs() + sameEventPairs());
    }

    /** 맞게 묶은 비율의 분모 = 시스템이 묶은 쌍 수(맞게 묶음 + 과병합). 화면이 분자/분모를 그대로 보일 때 쓴다. */
    public int mergedPairs() {
        return correctlyMerged + overMerged;
    }

    /** 빠짐없이 묶은 비율의 분모 = 같은 사건 쌍 수(맞게 묶음 + 과소병합). */
    public int sameEventPairs() {
        return correctlyMerged + missed;
    }

    // 분모 0 은 empty: 비율을 0 이나 100 으로 지어내지 않는다
    private static OptionalDouble ratio(int numerator, int denominator) {
        return denominator == 0 ? OptionalDouble.empty() : OptionalDouble.of(numerator * 100.0 / denominator);
    }

    // 과소병합(MISSED), 과병합(OVER_MERGED)
    public enum ErrorType { MISSED, OVER_MERGED }

    public record WrongPair(long alertA, long alertB, ErrorType kind) {
    }
}
