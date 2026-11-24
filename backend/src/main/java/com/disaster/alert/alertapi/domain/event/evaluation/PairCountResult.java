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

    /**
     * 과거 일일 점수를 쌍별 기록 없이 읽도록 기존 여섯 집계에서 맞게 나눔을 복원한다.
     * 두 알림이 배정된 쌍의 집계이므로 미배정은 0이며, 형식 실패는 평가 분모에서만 제외한다.
     */
    public static PairCountResult fromDailyCounts(int pairCount, int correctlyMerged, int missed,
                                                  int overMerged, int unsureCount, int formatFailureCount) {
        // 1) 전체 판정 수에는 제외 쌍도 들어 있다. 여섯 범주의 나머지가 맞게 나눔이며 음수를 0으로 보정하지 않는다.
        int separated = pairCount - correctlyMerged - missed - overMerged - unsureCount - formatFailureCount;
        // 2) 오류 비율도 기존 개수에서 구한다. 쌍별 기록을 읽지 않았으므로 틀린 쌍 목록은 비워 둔다.
        return new PairCountResult(correctlyMerged, missed, overMerged, separated, unsureCount, 0,
                ratio(missed, correctlyMerged + missed), ratio(overMerged, correctlyMerged + overMerged), List.of());
    }

    /** 다른 사건 쌍 중 따로 둔 비율(%). 다른 사건 쌍 자체가 없으면 empty다. */
    public OptionalDouble separation() {
        return ratio(correctlySeparated, differentEventPairs());
    }

    /**
     * 구성비와 무관하게 두 클래스의 정답률을 같은 비중으로 읽는 대표 지표(%).
     * 한쪽 클래스라도 없으면 전체 품질을 비교할 수 없으므로 empty다.
     */
    public OptionalDouble balancedAccuracy() {
        // 1) 한쪽 분모가 없을 때 다른 쪽 정답률만 대표 점수로 쓰지 않는다.
        int same = sameEventPairs();
        int different = differentEventPairs();
        if (same <= 0 || different <= 0) {
            return OptionalDouble.empty();
        }
        // 2) 표시용으로 반올림된 비율을 평균내지 않고 원래 개수에서 바로 계산한다.
        return OptionalDouble.of((correctlyMerged * 100.0 / same + correctlySeparated * 100.0 / different) / 2);
    }

    /** 섞지 않은 비율의 분모 = 다른 사건 쌍 수(과병합 + 맞게 나눔). */
    public int differentEventPairs() {
        return overMerged + correctlySeparated;
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
