package com.disaster.alert.alertapi.domain.event.evaluation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class TrivialBaselinesTest {

    // 같은 평가 집합을 판단 없이 전부 묶거나 전부 나누면 한 클래스만 맞아 두 기준선 모두 50%다.
    // Red 단계는 기준선 결과가 null이라 결과 존재 단언에서 실패하며, NPE로 실패하지 않는다.
    @Test
    void 전부_묶음과_전부_나눔의_균형_정확도는_모두_50퍼센트다() {
        // Given: 같은 사건 20쌍·다른 사건 10쌍이고, 모르겠음 7·미배정 3은 비교 집합에 넣지 않는다.
        PairCountResult counts = new PairCountResult(16, 4, 1, 9, 7, 3,
                OptionalDouble.empty(), OptionalDouble.empty(), List.of());

        // When: 시스템을 잰 것과 같은 쌍 집합으로 두 가지 무판단 기준선을 만든다.
        PairCountResult allMerged = TrivialBaselines.allMerged(counts);
        PairCountResult allSeparated = TrivialBaselines.allSeparated(counts);

        // Then: 전부 묶음은 SAME만, 전부 나눔은 DIFF만 맞아 각각 (100%+0%)/2인 50%다.
        assertAll(
                () -> {
                    assertThat(allMerged).isNotNull();
                    assertThat(allMerged.balancedAccuracy()).hasValue(50.0);
                },
                () -> {
                    assertThat(allSeparated).isNotNull();
                    assertThat(allSeparated.balancedAccuracy()).hasValue(50.0);
                });
    }
}
