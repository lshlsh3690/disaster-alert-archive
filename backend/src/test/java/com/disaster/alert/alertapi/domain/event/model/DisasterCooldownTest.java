package com.disaster.alert.alertapi.domain.event.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DisasterCooldown} 순수 정적 유틸리티 단위 테스트.
 * 외부 의존성이 없으므로 Spring 컨텍스트 없이 순수 JUnit으로 검증한다.
 */
@DisplayName("DisasterCooldown")
class DisasterCooldownTest {

    @DisplayName("LONG_TYPES(168h)에 속한 재난 유형은 168시간을 반환한다")
    @ParameterizedTest(name = "{0} -> 168h")
    @ValueSource(strings = {"산불", "지진", "지진해일", "폭염", "한파", "전염병", "가축질병", "가뭄"})
    void longTypesReturn168Hours(String disasterType) {
        assertThat(DisasterCooldown.hoursFor(disasterType)).isEqualTo(DisasterCooldown.LONG_HOURS);
    }

    @DisplayName("MID_TYPES(72h)에 속한 재난 유형은 72시간을 반환한다")
    @ParameterizedTest(name = "{0} -> 72h")
    @ValueSource(strings = {"태풍", "홍수", "호우", "대설", "산사태", "풍랑", "황사", "환경오염", "미세먼지", "에너지", "화재"})
    void midTypesReturn72Hours(String disasterType) {
        assertThat(DisasterCooldown.hoursFor(disasterType)).isEqualTo(DisasterCooldown.MID_HOURS);
    }

    @DisplayName("SHORT_TYPES(24h)에 속한 재난 유형은 24시간을 반환한다")
    @ParameterizedTest(name = "{0} -> 24h")
    @ValueSource(strings = {"붕괴", "폭발", "강풍", "정전", "수도", "통신", "금융",
            "테러", "민방공", "교통사고", "교통통제", "교통", "건조", "안개"})
    void shortTypesReturn24Hours(String disasterType) {
        assertThat(DisasterCooldown.hoursFor(disasterType)).isEqualTo(DisasterCooldown.SHORT_HOURS);
    }

    @Test
    @DisplayName("null 입력 시 기본값(72h)을 반환한다")
    void nullReturnsDefaultHours() {
        assertThat(DisasterCooldown.hoursFor(null)).isEqualTo(DisasterCooldown.DEFAULT_HOURS);
    }

    @DisplayName("미등록/빈 문자열 유형은 기본값(72h)을 반환한다")
    @ParameterizedTest(name = "\"{0}\" -> 72h(default)")
    @ValueSource(strings = {"기타", "미상", "재난문자", ""})
    void unregisteredTypeReturnsDefaultHours(String disasterType) {
        assertThat(DisasterCooldown.hoursFor(disasterType)).isEqualTo(DisasterCooldown.DEFAULT_HOURS);
    }

    @DisplayName("앞뒤 공백은 trim 되어 정상 매칭된다")
    @ParameterizedTest(name = "\"{0}\" -> trim 후 매칭")
    @CsvSource({
            "' 산불 ', 168",
            "'산불 ', 168",
            "' 산불', 168",
            "' 화재 ', 72"
    })
    void trimsWhitespaceBeforeLookup(String disasterType, int expectedHours) {
        assertThat(DisasterCooldown.hoursFor(disasterType)).isEqualTo(expectedHours);
    }

    @Test
    @DisplayName("탭/개행 등 다른 공백 문자도 trim 되어 정상 매칭된다")
    void trimsTabAndNewlineWhitespace() {
        assertThat(DisasterCooldown.hoursFor("\t태풍\n")).isEqualTo(DisasterCooldown.MID_HOURS);
    }

    @Test
    @DisplayName("상수값 자체가 문서화된 값과 일치한다")
    void constantsMatchDocumentedValues() {
        assertThat(DisasterCooldown.LONG_HOURS).isEqualTo(168);
        assertThat(DisasterCooldown.MID_HOURS).isEqualTo(72);
        assertThat(DisasterCooldown.SHORT_HOURS).isEqualTo(24);
        assertThat(DisasterCooldown.DEFAULT_HOURS).isEqualTo(72);
    }

    // ---- allowsLlmFallbackMerge: 화재 한정 LLM 폴백 병합 시간 상한 ----

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 12, 12, 0);

    @Test
    @DisplayName("화재: 마지막 알림 1시간 전이고 이벤트 시작 이후면 병합을 허용한다")
    void fireAllowsMergeWhenLastAlertIsRecent() {
        assertThat(DisasterCooldown.allowsLlmFallbackMerge(
                "화재", NOW.minusHours(2), NOW.minusHours(1), NOW)).isTrue();
    }

    @Test
    @DisplayName("화재: 이벤트가 5일 전에 시작했어도 마지막 알림이 72시간 이내면 허용한다 (첫 알림이 아니라 마지막 알림 기준)")
    void fireUsesLastAlertNotFirstAlert() {
        assertThat(DisasterCooldown.allowsLlmFallbackMerge(
                "화재", NOW.minusDays(5), NOW.minusHours(1), NOW)).isTrue();
    }

    @Test
    @DisplayName("화재: 마지막 알림이 정확히 72시간 전이면 경계 포함으로 허용한다")
    void fireAllowsExactly72HoursBoundary() {
        assertThat(DisasterCooldown.allowsLlmFallbackMerge(
                "화재", NOW.minusDays(5), NOW.minusHours(72), NOW)).isTrue();
    }

    @Test
    @DisplayName("화재: 마지막 알림이 72시간 + 1분 전이면 병합을 거부한다")
    void fireRejectsBeyond72Hours() {
        assertThat(DisasterCooldown.allowsLlmFallbackMerge(
                "화재", NOW.minusDays(5), NOW.minusHours(72).minusMinutes(1), NOW)).isFalse();
    }

    @Test
    @DisplayName("화재: 알림이 이벤트 시작보다 이르면 병합을 거부한다")
    void fireRejectsAlertBeforeEventStart() {
        assertThat(DisasterCooldown.allowsLlmFallbackMerge(
                "화재", NOW.plusHours(1), NOW.plusHours(2), NOW)).isFalse();
    }

    @Test
    @DisplayName("화재: 유형 앞뒤 공백은 trim 되어 동일하게 제한된다")
    void fireTypeIsTrimmed() {
        assertThat(DisasterCooldown.allowsLlmFallbackMerge(
                " 화재 ", NOW.minusDays(10), NOW.minusDays(5), NOW)).isFalse();
    }

    @DisplayName("비화재 유형은 마지막 알림이 30일 전이어도 항상 허용한다")
    @ParameterizedTest(name = "{0} -> 항상 true (오래된 이벤트)")
    @ValueSource(strings = {"기타", "교통사고", "산불", ""})
    void nonFireAllowsOldEvent(String type) {
        assertThat(DisasterCooldown.allowsLlmFallbackMerge(
                type, NOW.minusDays(40), NOW.minusDays(30), NOW)).isTrue();
    }

    @DisplayName("비화재 유형은 알림이 이벤트 시작보다 이르러도 항상 허용한다")
    @ParameterizedTest(name = "{0} -> 항상 true (시작 이전 알림)")
    @ValueSource(strings = {"기타", "교통사고"})
    void nonFireAllowsAlertBeforeStart(String type) {
        assertThat(DisasterCooldown.allowsLlmFallbackMerge(
                type, NOW.plusHours(1), NOW.plusHours(2), NOW)).isTrue();
    }

    @DisplayName("유형이 null 이면 어떤 시각 조합이든 허용한다")
    @ParameterizedTest(name = "null -> 항상 true")
    @NullSource
    void nullTypeAlwaysAllows(String type) {
        assertThat(DisasterCooldown.allowsLlmFallbackMerge(
                type, NOW.minusDays(40), NOW.minusDays(30), NOW)).isTrue();
        assertThat(DisasterCooldown.allowsLlmFallbackMerge(
                type, NOW.plusHours(1), NOW.plusHours(2), NOW)).isTrue();
    }
}
