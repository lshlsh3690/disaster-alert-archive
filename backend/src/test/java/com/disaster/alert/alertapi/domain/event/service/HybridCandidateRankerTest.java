package com.disaster.alert.alertapi.domain.event.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link HybridCandidateRanker} Red 테스트 — RRF(Reciprocal Rank Fusion) 순수 로직.
 * score = Σ 1/(k + rank), rank 는 1부터. 클래스가 아직 없으므로 컴파일 오류가 Red 사유다.
 */
class HybridCandidateRankerTest {

    private static final int K = 60;

    @Test
    @DisplayName("구체 수치 예(k=60): 양쪽 모두 있는 이벤트가 앞서고 점수순으로 정렬된다")
    void concreteScoresWithK60() {
        // vector:  [10, 20, 30]   keyword: [20, 40, 10]
        // 10: 1/61 + 1/63 = 0.016393 + 0.015873 = 0.032266
        // 20: 1/62 + 1/61 = 0.016129 + 0.016393 = 0.032522
        // 30: 1/63                              = 0.015873
        // 40: 1/62                              = 0.016129
        List<Long> result = HybridCandidateRanker.fuse(List.of(10L, 20L, 30L), List.of(20L, 40L, 10L), K, 10);

        assertThat(result).containsExactly(20L, 10L, 40L, 30L);
    }

    @Test
    @DisplayName("양쪽 리스트에 모두 있는 이벤트가 한쪽에만 있는 이벤트보다 앞선다")
    void presentInBothBeatsSingleList() {
        // 1(벡터 1위)은 한쪽만, 2(벡터 2위/키워드 3위)는 양쪽 -> 2: 1/62+1/63 > 1: 1/61
        List<Long> result = HybridCandidateRanker.fuse(List.of(1L, 2L), List.of(9L, 8L, 2L), K, 10);

        assertThat(result.indexOf(2L)).isLessThan(result.indexOf(1L));
        assertThat(result.indexOf(2L)).isLessThan(result.indexOf(9L));
    }

    @Test
    @DisplayName("키워드 리스트가 비어도 벡터 순서 그대로 동작한다")
    void emptyKeywordList() {
        assertThat(HybridCandidateRanker.fuse(List.of(3L, 1L, 2L), List.of(), K, 10))
                .containsExactly(3L, 1L, 2L);
    }

    @Test
    @DisplayName("벡터 리스트가 비어도 키워드 순서 그대로 동작한다")
    void emptyVectorList() {
        assertThat(HybridCandidateRanker.fuse(List.of(), List.of(5L, 4L), K, 10))
                .containsExactly(5L, 4L);
    }

    @Test
    @DisplayName("둘 다 비면 빈 리스트")
    void bothEmpty() {
        assertThat(HybridCandidateRanker.fuse(List.of(), List.of(), K, 10)).isEmpty();
    }

    @Test
    @DisplayName("topN 으로 자른다")
    void truncatesToTopN() {
        List<Long> result = HybridCandidateRanker.fuse(List.of(1L, 2L, 3L), List.of(4L, 5L), K, 2);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("동점이면 벡터 순위가 높은 쪽이 우선한다(결정적)")
    void tieBrokenByVectorRank() {
        // 1: 벡터 1위 -> 1/61, 2: 키워드 1위 -> 1/61 : 동점. 벡터에 있는 1 이 먼저.
        assertThat(HybridCandidateRanker.fuse(List.of(1L), List.of(2L), K, 10))
                .containsExactly(1L, 2L);

        // 벡터 1위 A / 키워드 2위, 벡터 2위 B / 키워드 1위 -> 점수 동일(1/61+1/62), 벡터 1위인 A 우선
        assertThat(HybridCandidateRanker.fuse(List.of(100L, 200L), List.of(200L, 100L), K, 10))
                .containsExactly(100L, 200L);
    }

    @Test
    @DisplayName("리스트 내 중복 id 는 첫 등장만 인정한다")
    void duplicatesCountFirstOccurrenceOnly() {
        // 중복이 인정되면 7 은 1/61 + 1/62 로 부풀어 올라 앞서지만, 첫 등장만 인정하면 7: 1/61, 8: 1/62.
        // 키워드의 8 은 1위(1/61) -> 8: 1/62 + 1/61 > 7: 1/61
        List<Long> result = HybridCandidateRanker.fuse(List.of(7L, 7L, 8L), List.of(8L), K, 10);

        assertThat(result).containsExactly(8L, 7L);
    }
}
