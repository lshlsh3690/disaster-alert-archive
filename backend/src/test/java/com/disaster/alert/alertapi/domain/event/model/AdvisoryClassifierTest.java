package com.disaster.alert.alertapi.domain.event.model;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AdvisoryClassifier} 테스트 — 유형별 안내성 분류 진입점(산불 위임). 폭염·한파는 에피소드형 앵커링으로 대체되어 다루지 않는다. 외부 의존성 없는 순수 로직.
 */
class AdvisoryClassifierTest {

    // ---- 산불: FireAlertClassifier 위임 ----

    @Test
    @DisplayName("산불은 FireAlertClassifier.isAdvisory 와 결과가 같다 - 안내 문구")
    void fireDelegates_advisoryCase() {
        String msg = "건조특보 발효 중, 산림 인접 소각 금지 및 산불 예방에 협조 바랍니다";
        assertThat(AdvisoryClassifier.isAdvisory("산불", msg, DisasterLevel.LEVEL_1))
                .isEqualTo(FireAlertClassifier.isAdvisory(msg, DisasterLevel.LEVEL_1))
                .isTrue();
    }

    @Test
    @DisplayName("산불은 FireAlertClassifier.isAdvisory 와 결과가 같다 - 사건 문구와 null 본문(산불은 null=안내)")
    void fireDelegates_incidentAndNullCase() {
        String msg = "용전리 산14 산불 진화 완료. 산불 예방에 협조";
        assertThat(AdvisoryClassifier.isAdvisory("산불", msg, DisasterLevel.LEVEL_1))
                .isEqualTo(FireAlertClassifier.isAdvisory(msg, DisasterLevel.LEVEL_1))
                .isFalse();
        assertThat(AdvisoryClassifier.isAdvisory("산불", null, DisasterLevel.LEVEL_1)).isTrue();
    }

    // ---- 경계 ----

    @Test
    @DisplayName("대상 외 유형, 빈/null 유형은 안내가 아니다")
    void otherOrMissingTypeIsNotAdvisory() {
        assertThat(AdvisoryClassifier.isAdvisory("호우", "오늘 무더위가 예상됩니다.▲야외활동 자제[홍성군]", DisasterLevel.LEVEL_1)).isFalse();
        assertThat(AdvisoryClassifier.isAdvisory("교통통제", "우회 바랍니다 유의", DisasterLevel.LEVEL_1)).isFalse();
        assertThat(AdvisoryClassifier.isAdvisory("", "오늘 무더위가 예상됩니다.▲야외활동 자제[홍성군]", DisasterLevel.LEVEL_1)).isFalse();
        assertThat(AdvisoryClassifier.isAdvisory(null, "오늘 무더위가 예상됩니다.▲야외활동 자제[홍성군]", DisasterLevel.LEVEL_1)).isFalse();
    }
}
