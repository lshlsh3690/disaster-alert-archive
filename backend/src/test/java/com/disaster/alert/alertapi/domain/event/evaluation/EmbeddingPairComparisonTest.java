package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.EmbeddingPairComparison.PairDistance;
import com.disaster.alert.alertapi.domain.event.evaluation.EmbeddingPairComparison.PairKey;
import com.disaster.alert.alertapi.domain.event.evaluation.EmbeddingPairComparison.Result;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 기준선과 후보 모델의 쌍 거리 비교 테스트. DB·Spring·임베딩 호출 없이 거리 값을 직접 넣는다.
 *
 * <p>두 테스트가 같은 입력을 쓴다. 기준선 지점은 0.15 이고, 기준선은 그 안에 다른 사건을 1쌍(1-2) 붙인다.
 * 후보는 거리 척도가 전체로 커서 0.15 를 그대로 쓰면 아무것도 못 붙인다.
 */
class EmbeddingPairComparisonTest {

    private static final List<PairDistance> BASELINE = List.of(
            // 다른 사건 3쌍 — 0.15 이하는 (1,2) 하나라서 K = 1
            new PairDistance(1, 2, Label.DIFF, 0.10),
            new PairDistance(3, 4, Label.DIFF, 0.30),
            new PairDistance(5, 6, Label.DIFF, 0.50),
            // 같은 사건 4쌍 — 0.15 이하는 (11,12)·(13,14) 두 쌍
            new PairDistance(11, 12, Label.SAME, 0.05),
            new PairDistance(13, 14, Label.SAME, 0.12),
            new PairDistance(15, 16, Label.SAME, 0.20),
            new PairDistance(17, 18, Label.SAME, 0.45));

    // 후보 목록은 일부러 기준선과 다른 순서로, 거리도 정렬하지 않고 넣는다 — 줄 위치가 아니라 알림 번호로 맞춰야 한다
    private static final List<PairDistance> CANDIDATE = List.of(
            new PairDistance(17, 18, Label.SAME, 0.32),
            // 후보의 다른 사건 거리를 작은 순으로 놓으면 0.30(3-4), 0.40(5-6), 0.60(1-2). 기준선과 순서가 다르다
            new PairDistance(1, 2, Label.DIFF, 0.60),
            new PairDistance(15, 16, Label.SAME, 0.25),
            new PairDistance(5, 6, Label.DIFF, 0.40),
            new PairDistance(13, 14, Label.SAME, 0.38),
            new PairDistance(3, 4, Label.DIFF, 0.30),
            new PairDistance(11, 12, Label.SAME, 0.20));

    /**
     * 과병합을 지금과 같게(다른 사건 K쌍) 묶어 두고 같은 사건을 몇 쌍 붙이는지 세야 모델끼리 비교가 된다.
     * 그래서 후보의 기준은 K번째와 K+1번째 다른 사건 거리의 가운데 값이다.
     * Red 단계에서는 뼈대가 모두 0 을 돌려줘서 첫 단언(K = 1)에서 실패한다.
     */
    @Test
    void 기준선이_붙이는_다른_사건_수만큼만_붙이는_거리를_정해_같은_사건_쌍을_센다() {
        // 실행: 기준선의 바로 병합 지점 0.15 로 비교한다
        Result result = EmbeddingPairComparison.compare(BASELINE, CANDIDATE, 0.15);

        // 검증: 기준선이 0.15 이하에 붙인 다른 사건은 1쌍
        assertThat(result.allowedDiffCount()).isEqualTo(1);
        // 후보의 1번째(0.30)와 2번째(0.40) 다른 사건 거리의 가운데 = 0.35 (실수 계산이라 오차 허용)
        assertThat(result.threshold()).isCloseTo(0.35, within(1e-9));
        // 리포트에 원문을 보일 두 쌍 — K=1 이면 기준이 이 두 쌍에만 달려 있어 사람이 확인해야 한다(research R2 한계)
        assertThat(result.thresholdPairs()).containsExactly(new PairKey(3, 4), new PairKey(5, 6));
        // 같은 사건: 기준선 2쌍, 후보는 0.35 이하인 0.32·0.25·0.20 의 3쌍
        assertThat(result.baselineSameMerged()).isEqualTo(2);
        assertThat(result.candidateSameMerged()).isEqualTo(3);
        // 후보가 붙인 다른 사건도 K 와 같은 1쌍 — 같은 조건에서 비교했다는 확인
        assertThat(result.candidateDiffMerged()).isEqualTo(1);
    }

    /**
     * 쌍 수의 차이(+1)만 보면 무엇이 좋아지고 나빠졌는지 알 수 없다. 원문을 읽을 수 있게 바뀐 쌍을 방향별로 돌려준다.
     * Red 단계에서는 뼈대가 빈 목록을 돌려줘서 첫 단언(새로 기준 안)에서 실패한다.
     */
    @Test
    void 기준선_대비_바뀐_같은_사건_쌍을_방향별로_돌려준다() {
        // 실행: 위 테스트와 같은 비교(후보 기준 0.35)
        Result result = EmbeddingPairComparison.compare(BASELINE, CANDIDATE, 0.15);

        // 검증: (15,16)·(17,18) 은 기준선에서는 밖이었는데 후보에서 안으로 들어왔다
        assertThat(result.newlyInside()).containsExactlyInAnyOrder(new PairKey(15, 16), new PairKey(17, 18));
        // (13,14) 는 기준선에서는 안(0.12)이었는데 후보에서 밖(0.38)이 됐다.
        // 양쪽 다 안인 (11,12)는 바뀐 것이 아니고, 다른 사건 쌍은 이 목록에 넣지 않는다
        assertThat(result.newlyOutside()).containsExactly(new PairKey(13, 14));
    }
}
