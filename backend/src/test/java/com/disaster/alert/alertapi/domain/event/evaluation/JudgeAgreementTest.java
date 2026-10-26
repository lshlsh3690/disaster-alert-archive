package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.JudgeAgreement.Item;
import com.disaster.alert.alertapi.domain.event.evaluation.JudgeAgreement.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JudgeAgreementTest {

    // 결정: 사람이 positive(SAME)라 한 쪽과 negative(DIFF)라 한 쪽을 각자의 분모로 따로 센다.
    // "사람이 같다고 한 N쌍 중 판사도 같다 M쌍" 의 M = positives - positiveMissed.size().
    // Red 단계: 뼈대가 0·빈 목록을 돌려줘서 positives 가 0 으로 나와 실패한다(기대 2).
    @Test
    @DisplayName("사람 positive/negative 를 각자의 분모로 세고 판사가 다르게 답한 id 를 쪽별로 모은다")
    void countsEachSideSeparatelyWithMissedIds() {
        List<Item<PairVerdict>> items = List.of(
                new Item<>(1, PairVerdict.SAME, PairVerdict.SAME),   // 사람 SAME, 판사도 SAME -> 일치
                new Item<>(2, PairVerdict.SAME, PairVerdict.DIFF),   // 사람 SAME, 판사 DIFF -> positiveMissed
                new Item<>(4, PairVerdict.DIFF, PairVerdict.DIFF),   // 사람 DIFF, 판사도 DIFF -> 일치
                new Item<>(5, PairVerdict.DIFF, PairVerdict.SAME),   // 사람 DIFF, 판사 SAME -> negativeMissed
                new Item<>(6, PairVerdict.DIFF, PairVerdict.DIFF));  // 사람 DIFF, 판사도 DIFF -> 일치

        Result result = JudgeAgreement.score(items, PairVerdict.SAME);

        assertThat(result.positives()).isEqualTo(2);
        assertThat(result.positiveMissed()).containsExactly(2L);
        assertThat(result.negatives()).isEqualTo(3);
        assertThat(result.negativeMissed()).containsExactly(5L);
        assertThat(result.undecided()).isEmpty();
    }

    // 결정: 판사 판정 불가(judge null)는 어느 분모에도 넣지 않고 undecided 로만 센다.
    // 분모에 넣으면 "판사가 틀렸다"로 오해되고, 빼면서 개수도 안 세면 조용히 사라진다.
    // Red 단계: 뼈대의 undecided 가 빈 목록이라 [3, 7] 기대가 실패한다.
    @Test
    @DisplayName("판정 불가(judge null)는 두 분모에서 빠지고 undecided 에만 들어간다")
    void undecidedIsExcludedFromBothDenominators() {
        List<Item<PairVerdict>> items = List.of(
                new Item<>(1, PairVerdict.SAME, PairVerdict.SAME),
                new Item<>(3, PairVerdict.SAME, null),               // positive 쪽 판정 불가
                new Item<>(4, PairVerdict.DIFF, PairVerdict.DIFF),
                new Item<>(7, PairVerdict.DIFF, null));              // negative 쪽 판정 불가

        Result result = JudgeAgreement.score(items, PairVerdict.SAME);

        // 판정 불가 둘이 빠지면 분모는 각 1 — 둘 다 판사가 일치했으므로 missed 는 비어 있다
        assertThat(result.positives()).isEqualTo(1);
        assertThat(result.negatives()).isEqualTo(1);
        assertThat(result.positiveMissed()).isEmpty();
        assertThat(result.negativeMissed()).isEmpty();
        assertThat(result.undecided()).containsExactly(3L, 7L);
    }

    // 결정: positive 는 인자로 받는다 — 분류에서는 INCIDENT 가 positive, 사람이 ADVISORY 라 한 쪽이 negative.
    // SAME 을 하드코딩한 구현은 이 입력에서 분모가 뒤바뀐다.
    // Red 단계: 뼈대가 0 을 돌려줘서 positives 기대(1)가 실패한다.
    @Test
    @DisplayName("positive 를 INCIDENT 로 주면 사람이 ADVISORY 라 한 쪽이 negative 분모가 된다")
    void positiveIsConfigurable() {
        List<Item<KindVerdict>> items = List.of(
                new Item<>(10, KindVerdict.INCIDENT, KindVerdict.INCIDENT),
                new Item<>(11, KindVerdict.ADVISORY, KindVerdict.INCIDENT));  // 안내를 사건으로 본 판사

        Result result = JudgeAgreement.score(items, KindVerdict.INCIDENT);

        assertThat(result.positives()).isEqualTo(1);
        assertThat(result.negatives()).isEqualTo(1);
        assertThat(result.negativeMissed()).containsExactly(11L);
    }
}
