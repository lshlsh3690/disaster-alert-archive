package com.disaster.alert.alertapi.global.translation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OpenAiTranslationClient#isSuspiciouslyLong(String, String)} 순수 단위 테스트.
 *
 * <p>이 판정은 외부 의존성이 없어 Spring 컨텍스트·DB·OpenAI 호출 없이 검증한다
 * (실제 번역 품질은 {@code DisasterAlertServiceOpenAiTranslationTest} 가 통합 테스트로 확인).
 *
 * <p>이 가드가 잘못 좁으면 정상 번역이 버려지고 해당 언어 사용자에게만 조용히 한국어 원문이
 * 노출된다 — 로그 한 줄 외에는 티가 안 나므로 경계값을 테스트로 못박아 둔다.
 */
class OpenAiTranslationClientTest {

    @Test
    @DisplayName("짧은 재난 유형은 배수 대신 하한(60자)이 적용돼 정상으로 본다")
    void 짧은_재난유형은_하한이_적용된다() {
        // "호우"(2자)에 배수만 적용하면 8자 제한이라 "Heavy rain"(10자) 같은 정상 번역도 걸린다.
        assertThat(OpenAiTranslationClient.isSuspiciouslyLong("호우", "Heavy rain")).isFalse();
        assertThat(OpenAiTranslationClient.isSuspiciouslyLong("폭염", "Cảnh báo nắng nóng")).isFalse();
    }

    @Test
    @DisplayName("하한 60자 경계 — 60자는 정상, 61자는 비정상")
    void 하한_경계값() {
        String shortSource = "호우";
        assertThat(OpenAiTranslationClient.isSuspiciouslyLong(shortSource, "a".repeat(60))).isFalse();
        assertThat(OpenAiTranslationClient.isSuspiciouslyLong(shortSource, "a".repeat(61))).isTrue();
    }

    @Test
    @DisplayName("실제 재난문자의 영어 번역(약 2.5배)은 정상으로 본다")
    void 영어_번역은_정상이다() {
        String source = "금일16시 경주동부(감포, 문무대왕, 양남면) 호우주의보 발효. 해안가, 야영장, "
                + "산사태취약지역 등 위험지역 접근을 피하시고 안전에 유의바랍니다. [경주시]";
        String translated = "Heavy rain advisory in effect for eastern Gyeongju (Gampo, Munmudaewang, "
                + "Yangnam-myeon) from 16:00 today. Please avoid approaching hazardous areas such as "
                + "coastlines, campsites, and landslide-vulnerable areas, and stay safe. [Gyeongju City]";

        assertThat(OpenAiTranslationClient.isSuspiciouslyLong(source, translated)).isFalse();
    }

    @Test
    @DisplayName("라틴 문자 계열의 팽창(원문의 4.75배)도 정상으로 본다")
    void 베트남어_수준의_팽창은_정상이다() {
        // 베트남어는 한글 1음절을 공백 포함 3~7자로 풀어 쓴다. 영어가 이미 2.5배인데 여기에
        // 성조 부호와 다음절 표기가 더해지면 4배를 넘길 수 있어, 가드가 4배면 정상 번역이 버려진다.
        String source = "가".repeat(40);
        String translated = "a".repeat(190); // 4.75배

        assertThat(OpenAiTranslationClient.isSuspiciouslyLong(source, translated)).isFalse();
    }

    @Test
    @DisplayName("설명이 덧붙은 응답(원문의 12배 이상)은 비정상으로 본다")
    void 설명이_덧붙으면_비정상이다() {
        String source = "가".repeat(40);
        String translated = "a".repeat(500); // 12.5배

        assertThat(OpenAiTranslationClient.isSuspiciouslyLong(source, translated)).isTrue();
    }

    @Test
    @DisplayName("null 입력은 길이 판정 대상이 아니다 (호출부에서 이미 걸러짐)")
    void null은_비정상으로_보지_않는다() {
        assertThat(OpenAiTranslationClient.isSuspiciouslyLong(null, "anything")).isFalse();
        assertThat(OpenAiTranslationClient.isSuspiciouslyLong("호우", null)).isFalse();
    }
}
