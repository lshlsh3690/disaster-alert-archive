package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.LabeledPair;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 평가 도구가 DB 에 저장된 사람 라벨을 "기준 시각의 묶음"으로 고정해 읽는 순수 규칙(JPA·Spring import 없음).
 * 튜닝 전후 비교가 같은 라벨로 돌도록 기준 시각 뒤에 수정된 행을 빼고, 결과 순서를 (alertA, alertB) 로 고정한다.
 * 호출하는 쪽(평가 도구)이 저장소에서 읽은 행을 {@link LabelRow} 로 바꿔 넘긴다.
 *
 * <p>Red 단계 뼈대 - 선택·정렬 로직은 없고, 틀린 기본값(빈 목록)만 돌려준다.
 */
public final class PairLabelSnapshot {

    private PairLabelSnapshot() {
    }

    /** 저장된 라벨 한 행. updatedAt 은 그 라벨이 마지막으로 저장·수정된 시각. */
    public record LabelRow(long alertA, long alertB, Label label, LocalDateTime updatedAt) {
    }

    /** updatedAt ≤ asOf 인 행만 (alertA, alertB) 오름차순으로 골라 평가용 쌍으로 바꾼다. */
    public static List<LabeledPair> select(List<LabelRow> rows, LocalDateTime asOf) {
        // Red 단계 뼈대 — 구현은 Green 에서
        return List.of();
    }
}
