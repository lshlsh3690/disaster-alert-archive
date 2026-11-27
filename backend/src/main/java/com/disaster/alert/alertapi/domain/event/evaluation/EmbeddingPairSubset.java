package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.model.EvaluationLabel.Relation;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 임베딩 모델 비교 1단계의 대상 쌍을 고르는 순수 규칙(specs/012 research R1, JPA·Spring import 없음).
 * 임베딩 거리로 병합이 정해지는 쌍만 남겨야 모델 차이가 보인다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EmbeddingPairSubset {

    /** 라벨된 쌍과 두 알림의 재난 유형. relation 이 null 이면 같은 시군구도 이웃 시군구도 아닌 먼 쌍이다. */
    public record TypedPair(long alertA, long alertB, Label label, Relation relation, String typeA, String typeB) {
    }

    /** 대상 쌍과 이유별 제외 수. 한 쌍은 제외 이유 하나에만 센다. */
    public record Result(List<TypedPair> targets, int excludedKeyPathType, int excludedNotNear, int excludedUnsure) {
    }

    /**
     * 제외 이유는 키 경로 유형 → 먼 쌍 → 모르겠음 순으로 하나만 센다(research R1 의 제외 집계 방식:
     * 키 경로이면서 모르겠음인 쌍은 키 경로로만 센다). 그래야 이유별 제외 수의 합이 빠진 쌍 수와 같다.
     *
     * @param keyPathTypes 임베딩 거리를 보지 않고 유형+시군구로 묶는 유형(regional-types·sustained-types)
     */
    public static Result select(List<TypedPair> pairs, Set<String> keyPathTypes) {
        List<TypedPair> targets = new ArrayList<>();
        int keyPath = 0;
        int notNear = 0;
        int unsure = 0;
        for (TypedPair p : pairs) {
            if (keyPathTypes.contains(p.typeA()) || keyPathTypes.contains(p.typeB())) {
                // 한쪽만 키 경로여도 그 알림은 유형+시군구 키로 묶여 거리가 병합을 정하지 않는다
                keyPath++;
            } else if (p.relation() == null) {
                // 시군구가 멀면 후보 검색에 오르지 않아 모델과 상관이 없다
                notNear++;
            } else if (p.label() == Label.UNSURE) {
                // 정답이 없는 쌍은 맞고 틀림을 셀 수 없다
                unsure++;
            } else {
                targets.add(p);
            }
        }
        return new Result(targets, keyPath, notNear, unsure);
    }
}
