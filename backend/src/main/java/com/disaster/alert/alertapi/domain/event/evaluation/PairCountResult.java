package com.disaster.alert.alertapi.domain.event.evaluation;

import java.util.List;
import java.util.OptionalDouble;

/**
 * PairCounter 가 세어 낸 결과. ReportFormatter 가 이 값을 읽어 리포트를 만든다.
 * 비율은 퍼센트 값(0~100)이고, 분모가 0 이면 empty 다.
 */
public record PairCountResult(int correctlyMerged, int missed, int overMerged,
                              int excludedUnsure, int excludedUnassigned,
                              OptionalDouble underMergeRate, OptionalDouble overMergeRate,
                              List<WrongPair> wrongPairs) {

    // 과소병합(놓침), 과병합(잘못 묶음)
    public enum WrongKind { MISSED, OVER_MERGED }

    public record WrongPair(long alertA, long alertB, WrongKind kind) {
    }
}
