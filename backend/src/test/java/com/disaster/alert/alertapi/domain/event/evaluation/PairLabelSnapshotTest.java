package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.LabeledPair;
import com.disaster.alert.alertapi.domain.event.evaluation.PairLabelSnapshot.LabelRow;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 평가용 라벨 묶음 선택 테스트. DB·Spring 없이 도는 단위 테스트다.
 */
class PairLabelSnapshotTest {

    /**
     * 튜닝 전후 점수를 비교하려면 두 번의 평가가 같은 라벨로 돌아야 한다. 그래서 기준 시각 뒤에 새로 매기거나 고친 라벨은
     * 묶음에서 빼고, 남은 쌍은 (alertA, alertB) 오름차순으로 돌려줘 틀린 쌍 표의 순서도 매번 같게 한다.
     * Red 단계에서는 뼈대가 항상 빈 목록을 돌려줘서, 첫 단언(두 쌍이 남는다)에서 실패한다.
     */
    @Test
    void 기준_시각_뒤에_수정된_라벨은_빠지고_남은_쌍은_알림_번호_순이다() {
        // 준비: 기준 시각은 11-18 12:00. 입력은 일부러 번호 순서를 섞어 넣는다(정렬을 하지 않는 구현을 걸러내려고)
        LocalDateTime asOf = LocalDateTime.of(2026, 11, 18, 12, 0);
        List<LabelRow> rows = List.of(
                // 기준 시각 전에 저장된 행 — 포함 대상이지만 번호가 큰 쌍이라 결과에서는 뒤로 가야 한다
                new LabelRow(300, 410, Label.DIFF, asOf.minusDays(1)),
                // 같은 alertA(150) 에 alertB 만 다른 두 행 — alertA 가 같을 때는 alertB 로 가려야 한다
                new LabelRow(150, 260, Label.UNSURE, asOf.minusHours(3)),
                new LabelRow(150, 205, Label.SAME, asOf.minusHours(5)),
                // 기준 시각 뒤(13:00)에 고친 행 — 그 사이 매긴 라벨이 섞이면 같은 평가가 재현되지 않으므로 빠져야 한다
                new LabelRow(101, 120, Label.SAME, asOf.plusHours(1)));

        // 실행: 기준 시각으로 묶음을 고른다
        List<LabeledPair> selected = PairLabelSnapshot.select(rows, asOf);

        // 검증: 뒤에 수정된 (101,120) 은 빠지고 세 쌍이 남는다. 라벨은 모르겠음(UNSURE)도 그대로 담는다
        // (채점에서 빼는 일은 기존 평가 규칙이 하므로 여기서 걸러내지 않는다)
        assertThat(selected).containsExactly(
                new LabeledPair(150, 205, Label.SAME),
                new LabeledPair(150, 260, Label.UNSURE),
                new LabeledPair(300, 410, Label.DIFF));
    }
}
