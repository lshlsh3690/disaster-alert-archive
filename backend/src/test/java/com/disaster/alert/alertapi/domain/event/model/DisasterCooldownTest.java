package com.disaster.alert.alertapi.domain.event.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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
    @ValueSource(strings = {"태풍", "홍수", "호우", "대설", "산사태", "풍랑", "황사", "환경오염", "미세먼지", "에너지"})
    void midTypesReturn72Hours(String disasterType) {
        assertThat(DisasterCooldown.hoursFor(disasterType)).isEqualTo(DisasterCooldown.MID_HOURS);
    }

    @DisplayName("SHORT_TYPES(24h)에 속한 재난 유형은 24시간을 반환한다")
    @ParameterizedTest(name = "{0} -> 24h")
    @ValueSource(strings = {"화재", "붕괴", "폭발", "강풍", "정전", "수도", "통신", "금융",
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
            "' 화재 ', 24"
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
}
