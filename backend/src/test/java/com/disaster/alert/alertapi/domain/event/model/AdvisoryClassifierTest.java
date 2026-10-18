package com.disaster.alert.alertapi.domain.event.model;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AdvisoryClassifier} 테스트 — 유형별 안내성 분류 진입점(산불 위임). 폭염·한파는 에피소드형 키 병합으로 대체되어 다루지 않는다. 외부 의존성 없는 순수 로직.
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

    // ---- 기타: 물놀이·수상안전 예방 안내문 ----

    @ParameterizedTest(name = "기타 물놀이·수상안전 예방 안내문은 안내성이다: {0}")
    @ValueSource(strings = {
            "수상안전사고 예방을 위해 ▲구명조끼 착용 ▲위험구역 진입금지 ▲야간, 음주수영 금지 ▲어린이는 반드시 보호자 동반 등 안전수칙을 준수하시기 바랍니다. [옥천군]",
            "물놀이 안전사고 예방을 위해 △준비운동, 구명조끼 필수 △야간 및 음주 수영 금지 △지정된 장소에서만 물놀이 등 안전수칙 준수 바랍니다. [울산광역시]",
            "수난사고로 인한 인명피해 예방을 위하여 안전한 장소에서 안전장비를 착용하고 안전수칙을 준수하여 주시기 바랍니다. [통영시]"
    })
    void etcWaterSafetyGuide_isAdvisory(String message) {
        assertThat(AdvisoryClassifier.isAdvisory("기타", message, DisasterLevel.LEVEL_1)).isTrue();
    }

    @ParameterizedTest(name = "기타 비안내 문구는 안내성이 아니다: {0}")
    @ValueSource(strings = {
            "광명시 옥길동151 인근 사슴5마리 탈출. 주민께서는 안전에 유의하시고 발견 시 신고 바랍니다. [광명시]",
            "시내버스 파업으로 운행이 중단됩니다. 셔틀버스를 이용해 주시기 바랍니다. [OO시]",
            "도로 결빙이 예상되오니 출퇴근길 안전운전에 유의하시기 바랍니다. [OO시]",
            "금일 10:03 금왕읍 삼봉리 641 일원에서 화학 물질 유출로 인근 주민은 즉시 대피하시기 바랍니다. [음성군]",
            "계곡에서 익수 사고가 발생하여 구조 중입니다. 물놀이 안전수칙 준수 바랍니다"
    })
    void etcNonAdvisoryMessages_areNotAdvisory(String message) {
        assertThat(AdvisoryClassifier.isAdvisory("기타", message, DisasterLevel.LEVEL_1)).isFalse();
    }

    @Test
    @DisplayName("기타 안내 문구여도 긴급도 LEVEL_2 이상이면 사건이다")
    void etcWaterSafetyGuide_highLevelIsNotAdvisory() {
        String msg = "물놀이 안전사고 예방을 위해 구명조끼 착용 등 안전수칙 준수 바랍니다. [울산광역시]";
        assertThat(AdvisoryClassifier.isAdvisory("기타", msg, DisasterLevel.LEVEL_2)).isFalse();
        assertThat(AdvisoryClassifier.isAdvisory("기타", msg, DisasterLevel.LEVEL_3)).isFalse();
    }

    @Test
    @DisplayName("기타 본문이 null/blank 이면 안내가 아니다 (산불과 다름)")
    void etcNullOrBlankMessage_isNotAdvisory() {
        assertThat(AdvisoryClassifier.isAdvisory("기타", null, DisasterLevel.LEVEL_1)).isFalse();
        assertThat(AdvisoryClassifier.isAdvisory("기타", "   ", DisasterLevel.LEVEL_1)).isFalse();
    }

    @Test
    @DisplayName("기타가 아닌 유형(호우)은 같은 물놀이 안내 문구여도 안내가 아니다")
    void waterSafetyGuide_otherTypeIsNotAdvisory() {
        String msg = "물놀이 안전사고 예방을 위해 구명조끼 착용 등 안전수칙 준수 바랍니다. [울산광역시]";
        assertThat(AdvisoryClassifier.isAdvisory("호우", msg, DisasterLevel.LEVEL_1)).isFalse();
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
