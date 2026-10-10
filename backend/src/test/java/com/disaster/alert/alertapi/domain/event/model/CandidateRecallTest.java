package com.disaster.alert.alertapi.domain.event.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link CandidateRecall} 테스트 — 후보 검색이 정답 기존 사건을 상위 k 안에 올렸는지(recall@k).
 * 외부 의존성 없는 순수 로직이므로 순수 JUnit 단위 테스트로 작성한다.
 */
class CandidateRecallTest {

    private static final double EPS = 1e-9;

    private static CandidateRecall.Case of(Set<Long> gold, Long... ranked) {
        return new CandidateRecall.Case(gold, List.of(ranked));
    }

    @Test
    @DisplayName("상위 k 안에 정답이 있으면 적중, 없으면 미적중: 2건 중 1건 적중 = 0.5")
    void basicRecall() {
        List<CandidateRecall.Case> cases = List.of(
                of(Set.of(10L), 10L, 20L, 30L),
                of(Set.of(99L), 1L, 2L, 3L));

        OptionalDouble r = CandidateRecall.recallAtK(cases, 3);

        assertThat(r).isPresent();
        assertThat(r.getAsDouble()).isEqualTo(0.5, within(EPS));
    }

    @Test
    @DisplayName("k=3 에서 정답이 4번째면 미적중, k=4 에서는 적중")
    void fourthIsMissAtK3() {
        List<CandidateRecall.Case> cases = List.of(of(Set.of(40L), 10L, 20L, 30L, 40L));

        assertThat(CandidateRecall.recallAtK(cases, 3).getAsDouble()).isEqualTo(0.0, within(EPS));
        assertThat(CandidateRecall.recallAtK(cases, 4).getAsDouble()).isEqualTo(1.0, within(EPS));
    }

    @Test
    @DisplayName("정답이 여러 개일 때 그중 하나라도 상위 k 안에 있으면 적중")
    void anyOfGoldCounts() {
        List<CandidateRecall.Case> cases = List.of(of(Set.of(7L, 8L), 1L, 8L, 2L));

        assertThat(CandidateRecall.recallAtK(cases, 2).getAsDouble()).isEqualTo(1.0, within(EPS));
    }

    @Test
    @DisplayName("정답 기존 사건이 없는 Case(goldEventIds 비어있음)는 분모에서 제외한다")
    void casesWithoutGoldAreExcludedFromDenominator() {
        List<CandidateRecall.Case> cases = List.of(
                of(Set.of(10L), 10L),       // 적중
                of(Set.of(), 1L, 2L, 3L),   // 신규 사건: 제외
                of(Set.of(), 10L));         // 제외 (후보에 뭐가 있든)

        assertThat(CandidateRecall.recallAtK(cases, 3).getAsDouble()).isEqualTo(1.0, within(EPS));
    }

    @Test
    @DisplayName("분모가 0이면(정답 있는 Case가 없으면) OptionalDouble.empty() — NaN/0.0 아님")
    void emptyDenominatorReturnsEmpty() {
        assertThat(CandidateRecall.recallAtK(List.of(), 3)).isEmpty();
        assertThat(CandidateRecall.recallAtK(List.of(of(Set.of(), 1L, 2L)), 3)).isEmpty();
    }

    @Test
    @DisplayName("후보가 k개 미만이어도 정상 동작한다")
    void fewerCandidatesThanK() {
        List<CandidateRecall.Case> cases = List.of(of(Set.of(2L), 1L, 2L));

        assertThat(CandidateRecall.recallAtK(cases, 10).getAsDouble()).isEqualTo(1.0, within(EPS));
    }

    @Test
    @DisplayName("후보가 비어 있으면 미적중으로 센다(분모에는 포함)")
    void emptyCandidatesIsMiss() {
        List<CandidateRecall.Case> cases = List.of(
                new CandidateRecall.Case(Set.of(5L), List.of()),
                of(Set.of(1L), 1L));

        assertThat(CandidateRecall.recallAtK(cases, 3).getAsDouble()).isEqualTo(0.5, within(EPS));
    }

    @Test
    @DisplayName("k 가 0 이하이면 IllegalArgumentException")
    void nonPositiveKThrows() {
        List<CandidateRecall.Case> cases = List.of(of(Set.of(1L), 1L));

        assertThatThrownBy(() -> CandidateRecall.recallAtK(cases, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CandidateRecall.recallAtK(cases, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
