package com.disaster.alert.alertapi.domain.event.model;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AdvisoryClassifier} 테스트 — 유형별 안내성 분류 진입점. 외부 의존성 없는 순수 로직.
 */
class AdvisoryClassifierTest {

    private static final String HEAT_GUIDE =
            "오늘 무더위가 예상됩니다.▲낮 시간 논·밭작업 및 야외활동 자제▲무더위쉼터 이용"
                    + "▲폭염안전수칙(물,그늘,휴식)을 준수하여 건강관리에 유의하시기 바랍니다.[홍성군]";
    private static final String COLD_GUIDE =
            "강한 한파 지속 ▲방한용품 착용 ▲한랭질환 주의 ... 안전에 유의 [영동군]";

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

    // ---- 폭염 ----

    @Test
    @DisplayName("폭염: 특보·피해 표현 없이 행동요령만 있는 일반 안내는 안내성이다")
    void heat_generalGuidanceIsAdvisory() {
        assertThat(AdvisoryClassifier.isAdvisory("폭염", HEAT_GUIDE, DisasterLevel.LEVEL_1)).isTrue();
    }

    @Test
    @DisplayName("폭염: LEVEL_2/LEVEL_3 은 본문과 무관하게 항상 사건이다")
    void heat_highLevelIsAlwaysIncident() {
        assertThat(AdvisoryClassifier.isAdvisory("폭염", HEAT_GUIDE, DisasterLevel.LEVEL_2)).isFalse();
        assertThat(AdvisoryClassifier.isAdvisory("폭염", HEAT_GUIDE, DisasterLevel.LEVEL_3)).isFalse();
    }

    @Test
    @DisplayName("폭염: 본문이 null/blank 면 안내가 아니다 - 판단 불가는 기존 경로 유지(산불의 null=안내와 반대)")
    void heat_nullOrBlankMessageIsNotAdvisory() {
        assertThat(AdvisoryClassifier.isAdvisory("폭염", null, DisasterLevel.LEVEL_1)).isFalse();
        assertThat(AdvisoryClassifier.isAdvisory("폭염", "   ", DisasterLevel.LEVEL_1)).isFalse();
    }

    @Test
    @DisplayName("폭염: 특보 자체를 알리는 문자(발효/발령/해제/격상/주의보/경보/특보)는 사건이다")
    void heat_warningNoticeIsIncident() {
        for (String msg : new String[]{
                "폭염주의보 발효, 야외활동 자제 바랍니다",
                "폭염경보 해제",
                "폭염특보 격상",
                "오늘 14시 폭염 발령"}) {
            assertThat(AdvisoryClassifier.isAdvisory("폭염", msg, DisasterLevel.LEVEL_1))
                    .as(msg).isFalse();
        }
    }

    @Test
    @DisplayName("폭염: 특보 표현과 안내 표현이 섞이면 보수적으로 사건 - 특보 표현이 있으면 기존 경로 유지")
    void heat_mixedWarningAndGuidanceIsIncident() {
        assertThat(AdvisoryClassifier.isAdvisory("폭염",
                "폭염경보 발령, 야외활동 자제 및 건강관리에 유의하시기 바랍니다", DisasterLevel.LEVEL_1)).isFalse();
    }

    @Test
    @DisplayName("폭염: 실제 발생을 말하는 표현(사망/사상자/부상/환자 발생)이 있으면 사건이다")
    void heat_damageExpressionIsIncident() {
        for (String msg : new String[]{
                "폭염으로 온열질환자 사망, 야외활동 자제",
                "폭염으로 사상자 발생, 유의 바랍니다",
                "폭염으로 부상자 접수 중, 유의",
                "온열질환자 3명 발생, 자제 바랍니다",
                "온열질환 환자 발생, 주의 바랍니다"}) {
            assertThat(AdvisoryClassifier.isAdvisory("폭염", msg, DisasterLevel.LEVEL_1))
                    .as(msg).isFalse();
        }
    }

    @Test
    @DisplayName("폭염: '사고'/'피해'/'정전' 일반 단어는 안내문 상투구라 사건 신호가 아니다")
    void heat_genericWordsAreStillAdvisory() {
        for (String msg : new String[]{
                "물놀이 안전사고 주의",
                "수상 안전사고 예방(음주 후 입수금지)",
                "낙상사고 주의, 야외활동 자제",
                "폭염 피해 예방을 위해 무더위쉼터를 이용하시기 바랍니다",
                "정전 시 한전(123)에 신고하고 냉방기 사용을 자제해 주세요"}) {
            assertThat(AdvisoryClassifier.isAdvisory("폭염", msg, DisasterLevel.LEVEL_1))
                    .as(msg).isTrue();
        }
    }

    @Test
    @DisplayName("폭염: 실제 발생 표현은 사건이다 - 온열질환자 N명 발생 / 온열질환자가 발생 / 사망 / 사상자 발생")
    void heat_actualOccurrenceIsIncident() {
        for (String msg : new String[]{
                "온열질환자 3명 발생",
                "온열질환자가 발생했습니다",
                "폭염으로 1명 사망",
                "사상자 발생 야외활동 자제"}) {
            assertThat(AdvisoryClassifier.isAdvisory("폭염", msg, DisasterLevel.LEVEL_1))
                    .as(msg).isFalse();
        }
    }

    // ---- 한파 ----

    @Test
    @DisplayName("한파: 빙판길 사고·동파·화재 등 상투구는 안내, 한랭질환자 N명 발생은 사건이다")
    void cold_genericWordsAdvisoryButOccurrenceIncident() {
        assertThat(AdvisoryClassifier.isAdvisory("한파",
                "빙판길 사고와 수도 동파, 난방기구 화재 등 안전에 유의", DisasterLevel.LEVEL_1)).isTrue();
        assertThat(AdvisoryClassifier.isAdvisory("한파",
                "한랭질환자 2명 발생", DisasterLevel.LEVEL_1)).isFalse();
        assertThat(AdvisoryClassifier.isAdvisory("한파",
                "한파로 사망자 발생", DisasterLevel.LEVEL_1)).isFalse();
    }

    // ---- 경계 ----

    @Test
    @DisplayName("대상 외 유형, 빈/null 유형은 안내가 아니다")
    void otherOrMissingTypeIsNotAdvisory() {
        assertThat(AdvisoryClassifier.isAdvisory("호우", HEAT_GUIDE, DisasterLevel.LEVEL_1)).isFalse();
        assertThat(AdvisoryClassifier.isAdvisory("교통통제", "우회 바랍니다 유의", DisasterLevel.LEVEL_1)).isFalse();
        assertThat(AdvisoryClassifier.isAdvisory("", HEAT_GUIDE, DisasterLevel.LEVEL_1)).isFalse();
        assertThat(AdvisoryClassifier.isAdvisory(null, HEAT_GUIDE, DisasterLevel.LEVEL_1)).isFalse();
    }
}
