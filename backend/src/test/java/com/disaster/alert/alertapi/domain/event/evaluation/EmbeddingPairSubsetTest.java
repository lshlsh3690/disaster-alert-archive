package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.EmbeddingPairSubset.Result;
import com.disaster.alert.alertapi.domain.event.evaluation.EmbeddingPairSubset.TypedPair;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.disaster.alert.alertapi.domain.event.model.EvaluationLabel.Relation.ADJACENT_SIGUNGU;
import static com.disaster.alert.alertapi.domain.event.model.EvaluationLabel.Relation.SAME_SIGUNGU;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 임베딩 모델 비교 1단계 대상 쌍 고르기 테스트. DB·Spring 없이 도는 단위 테스트다.
 */
class EmbeddingPairSubsetTest {

    /**
     * 모델을 바꿔도 결과가 달라질 수 없는 쌍(유형+시군구 키로 묶는 유형, 후보 검색에 오르지 않는 먼 쌍)과
     * 정답이 없는 쌍(모르겠음)을 섞어 세면 모델 차이가 묽어진다. 그래서 이 쌍들을 빼고, 이유별로 몇 쌍을 뺐는지 남긴다.
     * Red 단계에서는 뼈대가 빈 대상 목록을 돌려줘서 첫 단언(대상 두 쌍)에서 실패한다.
     */
    @Test
    void 임베딩_거리로_병합이_정해지는_쌍만_남기고_이유별_제외_수를_센다() {
        // 준비: 키 경로 유형 목록. 실제 실험에서는 설정(regional-types·sustained-types)에서 읽은 값이 들어온다
        Set<String> keyPathTypes = Set.of("산불", "호우", "폭염");
        // 대상 1 — 같은 시군구의 같은 사건
        TypedPair sameSigungu = new TypedPair(1, 2, Label.SAME, SAME_SIGUNGU, "화재", "화재");
        // 대상 2 — 이웃 시군구의 다른 사건. 두 유형이 서로 달라도 키 경로 유형이 아니면 대상이다
        TypedPair adjacent = new TypedPair(3, 4, Label.DIFF, ADJACENT_SIGUNGU, "화재", "교통사고");
        List<TypedPair> pairs = List.of(
                sameSigungu,
                adjacent,
                // 뒤쪽 알림만 키 경로 유형(산불) — 한쪽만 걸려도 빠진다
                new TypedPair(5, 6, Label.SAME, SAME_SIGUNGU, "화재", "산불"),
                // 앞쪽 알림만 키 경로 유형(폭염) — 어느 쪽이든 같아야 한다
                new TypedPair(7, 8, Label.DIFF, SAME_SIGUNGU, "폭염", "기타"),
                // 키 경로이면서 모르겠음 — 키 경로로만 한 번 센다(research R1 의 제외 집계 방식)
                new TypedPair(9, 10, Label.UNSURE, SAME_SIGUNGU, "호우", "호우"),
                // 지역 관계가 없는(null) 먼 쌍
                new TypedPair(11, 12, Label.SAME, null, "화재", "화재"),
                // 대상 유형·같은 시군구지만 사람이 모르겠음으로 둔 쌍
                new TypedPair(13, 14, Label.UNSURE, SAME_SIGUNGU, "화재", "화재"));

        // 실행: 대상 쌍을 고른다
        Result result = EmbeddingPairSubset.select(pairs, keyPathTypes);

        // 검증: 일곱 쌍 중 두 쌍만 남는다
        assertThat(result.targets()).containsExactlyInAnyOrder(sameSigungu, adjacent);
        // 키 경로 3쌍(5-6, 7-8, 9-10), 먼 쌍 1쌍, 모르겠음 1쌍. 합이 5 라 이중 집계가 없다
        assertThat(result.excludedKeyPathType()).isEqualTo(3);
        assertThat(result.excludedNotNear()).isEqualTo(1);
        assertThat(result.excludedUnsure()).isEqualTo(1);
    }
}
