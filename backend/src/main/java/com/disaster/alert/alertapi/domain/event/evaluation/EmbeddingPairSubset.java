package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.model.EvaluationLabel.Relation;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * 임베딩 모델 비교 1단계의 대상 쌍을 고르는 순수 규칙(specs/012 research R1, JPA·Spring import 없음).
 * 임베딩 거리로 병합이 정해지는 쌍만 남겨야 모델 차이가 보인다.
 */
// Red 단계 뼈대 — 구현은 Green 에서
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EmbeddingPairSubset {

    /** 라벨된 쌍과 두 알림의 재난 유형. relation 이 null 이면 같은 시군구도 이웃 시군구도 아닌 먼 쌍이다. */
    public record TypedPair(long alertA, long alertB, Label label, Relation relation, String typeA, String typeB) {
    }

    /** 대상 쌍과 이유별 제외 수. 한 쌍은 제외 이유 하나에만 센다. */
    public record Result(List<TypedPair> targets, int excludedKeyPathType, int excludedNotNear, int excludedUnsure) {
    }

    /**
     * @param keyPathTypes 임베딩 거리를 보지 않고 유형+시군구로 묶는 유형(regional-types·sustained-types)
     */
    public static Result select(List<TypedPair> pairs, Set<String> keyPathTypes) {
        return new Result(List.of(), 0, 0, 0);
    }
}
