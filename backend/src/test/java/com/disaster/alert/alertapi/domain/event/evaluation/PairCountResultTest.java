package com.disaster.alert.alertapi.domain.event.evaluation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PairCountResultTest {

    // 전체를 같은 배수로 늘리면 구성비가 그대로이므로, 같은 사건 쌍만 2배로 늘려야 불변성을 확인할 수 있다.
    // Red 단계의 균형 정확도는 empty라 두 결과 모두 85.0%여야 한다는 단언에서 실패한다.
    @Test
    void 같은_사건_쌍만_두배여도_균형_정확도는_85퍼센트다() {
        // Given: (맞게 묶음, 과소병합, 과병합, 맞게 나눔)의 구성비를 1:1에서 2:1로 바꾼다.
        PairCountResult balanced = new PairCountResult(8, 2, 1, 9, 0, 0,
                OptionalDouble.empty(), OptionalDouble.empty(), List.of());
        PairCountResult moreSamePairs = new PairCountResult(16, 4, 1, 9, 0, 0,
                OptionalDouble.empty(), OptionalDouble.empty(), List.of());

        // When: 두 결과의 개수에서 대표 지표를 구한다. 반올림한 비율을 다시 평균내지 않는다.
        OptionalDouble balancedAccuracy = balanced.balancedAccuracy();
        OptionalDouble moreSameAccuracy = moreSamePairs.balancedAccuracy();

        // Then: 두 클래스 정답률은 각각 80%·90% 그대로여서 구성비와 무관하게 평균은 85%다.
        assertAll(
                () -> assertThat(balancedAccuracy).hasValue(85.0),
                () -> assertThat(moreSameAccuracy).hasValue(85.0));
    }

    // 쌍별 이력이 없어도 기존 여섯 일일 집계값에서 맞게 나눔을 복원해야 과거 점수를 읽을 수 있다.
    // Red 단계의 팩토리는 모든 개수를 0으로 돌려주므로 맞게 나눔 50쌍 단언에서 실패한다.
    @Test
    void 기존_일일_집계에서_맞게_나눈_50쌍을_복원한다() {
        // Given: 전체 100쌍 중 맞게 묶음 30·과소병합 10·과병합 5·모르겠음 4·형식 실패 1이다.
        int pairCount = 100;

        // When: 쌍별 기록을 조회하지 않고 저장된 집계만 순수 팩토리에 전달한다.
        PairCountResult result = PairCountResult.fromDailyCounts(pairCount, 30, 10, 5, 4, 1);

        // Then: 이미 설명된 50쌍을 전체에서 빼면 남은 50쌍이 맞게 나눔이다. 기존 세 개수도 유지한다.
        assertThat(result).isNotNull();
        assertThat(result.correctlySeparated()).isEqualTo(50);
        assertThat(result.correctlyMerged()).isEqualTo(30);
        assertThat(result.missed()).isEqualTo(10);
        assertThat(result.overMerged()).isEqualTo(5);
    }
}
