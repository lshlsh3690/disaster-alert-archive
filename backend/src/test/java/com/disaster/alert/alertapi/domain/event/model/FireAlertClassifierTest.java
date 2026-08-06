package com.disaster.alert.alertapi.domain.event.model;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FireAlertClassifier} 특성화(characterization) 테스트 — 외부 의존성 없는 순수 정규식 판정 로직.
 * DB/Spring 컨텍스트가 필요 없으므로 순수 JUnit 단위 테스트로 작성한다.
 */
class FireAlertClassifierTest {

    @Test
    @DisplayName("LEVEL_2(긴급재난)는 본문 내용과 무관하게 항상 사건(INCIDENT)이다")
    void level2AlwaysIncidentRegardlessOfMessage() {
        assertThat(FireAlertClassifier.isIncident("아무 상관없는 안내 문구", DisasterLevel.LEVEL_2)).isTrue();
        assertThat(FireAlertClassifier.isIncident(null, DisasterLevel.LEVEL_2)).isTrue();
        assertThat(FireAlertClassifier.isAdvisory("아무 상관없는 안내 문구", DisasterLevel.LEVEL_2)).isFalse();
    }

    @Test
    @DisplayName("LEVEL_3(위급재난)는 본문 내용과 무관하게 항상 사건(INCIDENT)이다")
    void level3AlwaysIncidentRegardlessOfMessage() {
        assertThat(FireAlertClassifier.isIncident("", DisasterLevel.LEVEL_3)).isTrue();
    }

    @Test
    @DisplayName("LEVEL_1이고 본문이 null 또는 공백이면 사건이 아니다(안내)")
    void level1NullOrBlankMessageIsAdvisory() {
        assertThat(FireAlertClassifier.isIncident(null, DisasterLevel.LEVEL_1)).isFalse();
        assertThat(FireAlertClassifier.isIncident("   ", DisasterLevel.LEVEL_1)).isFalse();
        assertThat(FireAlertClassifier.isAdvisory(null, DisasterLevel.LEVEL_1)).isTrue();
    }

    @Test
    @DisplayName("강신호 마커(진화)가 있으면 예방 보일러플레이트가 붙어도 사건으로 판정한다")
    void incidentMarkerWinsEvenWithAdvisoryBoilerplate() {
        String message = "용전리 산14 산불 진화 완료. 산불 예방에 협조";

        assertThat(FireAlertClassifier.isIncident(message, DisasterLevel.LEVEL_1)).isTrue();
    }

    @Test
    @DisplayName("'불길' 단독 마커만으로도 사건으로 판정한다")
    void fireFlameMarkerAloneIsIncident() {
        assertThat(FireAlertClassifier.isIncident("불길", DisasterLevel.LEVEL_1)).isTrue();
    }

    @Test
    @DisplayName("미세위치(산N)와 산불 언급이 있고 안내 조건문이 없으면 사건으로 판정한다")
    void microLocationWithFireOccurrenceAndNoAdvisoryConditionIsIncident() {
        String message = "OO군 OO읍 산24-5 일대에서 산불이 발생했습니다";

        assertThat(FireAlertClassifier.isIncident(message, DisasterLevel.LEVEL_1)).isTrue();
    }

    @Test
    @DisplayName("미세위치와 산불 언급이 있어도 안내 조건문(건조특보/예방 등)이 있으면 안내로 되돌린다")
    void advisoryConditionOverridesMicroLocationAndFireOccurrence() {
        String message = "OO시 OO읍 산15 일대 건조특보 발령, 산불 예방에 협조 바랍니다";

        assertThat(FireAlertClassifier.isIncident(message, DisasterLevel.LEVEL_1)).isFalse();
        assertThat(FireAlertClassifier.isAdvisory(message, DisasterLevel.LEVEL_1)).isTrue();
    }

    @Test
    @DisplayName("'잔불'만 언급되고 미세위치가 없는 예방 보일러플레이트는 사건이 아니다(안내)")
    void zanbulBoilerplateWithoutMicroLocationIsAdvisory() {
        String message = "화목보일러 사용 후 잔불은 완전히 소화하고 확인해주시기 바랍니다. 산불 예방에 협조해주세요";

        assertThat(FireAlertClassifier.isIncident(message, DisasterLevel.LEVEL_1)).isFalse();
    }

    @Test
    @DisplayName("산불/화재 언급 없이 미세위치(번지)만 있으면 사건이 아니다")
    void microLocationWithoutFireOccurrenceIsAdvisory() {
        String message = "OO읍 123번지 인근 도로 통제";

        assertThat(FireAlertClassifier.isIncident(message, DisasterLevel.LEVEL_1)).isFalse();
    }

    @Test
    @DisplayName("미세위치 없이 산불 언급만 있는 시군구 단위 안내문은 사건이 아니다")
    void fireOccurrenceWithoutMicroLocationIsAdvisory() {
        String message = "OO시 전역에 산불 주의 안내";

        assertThat(FireAlertClassifier.isIncident(message, DisasterLevel.LEVEL_1)).isFalse();
    }

    @Test
    @DisplayName("isAdvisory는 isIncident의 정확한 반대값이다")
    void isAdvisoryIsExactNegationOfIsIncident() {
        String incidentMessage = "불길";
        String advisoryMessage = "OO시 전역에 산불 주의 안내";

        assertThat(FireAlertClassifier.isAdvisory(incidentMessage, DisasterLevel.LEVEL_1))
                .isEqualTo(!FireAlertClassifier.isIncident(incidentMessage, DisasterLevel.LEVEL_1));
        assertThat(FireAlertClassifier.isAdvisory(advisoryMessage, DisasterLevel.LEVEL_1))
                .isEqualTo(!FireAlertClassifier.isIncident(advisoryMessage, DisasterLevel.LEVEL_1));
    }
}
