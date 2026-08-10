package com.disaster.alert.alertapi.global.translation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OpenAiTranslationClient#isSuspiciouslyLong(String, String)} 순수 단위 테스트.
 *
 * <p>이 판정은 외부 의존성이 없어 Spring 컨텍스트·DB·OpenAI 호출 없이 검증한다
 * (실제 번역 품질은 {@code OpenAiTranslationClientRealApiTest} 가 통합 테스트로 확인).
 *
 * <p>이 가드가 잘못 좁으면 정상 번역이 버려지고 해당 언어 사용자에게만 조용히 한국어 원문이
 * 노출된다 — 로그 한 줄 외에는 티가 안 나므로 경계값을 테스트로 못박아 둔다.
 */
@DisplayName("OpenAiTranslationClient.isSuspiciouslyLong")
class OpenAiTranslationClientTest {

    @Test
    @DisplayName("짧은 재난 유형은 배수 대신 하한(60자)이 적용돼 정상으로 본다")
    void shortDisasterTypeUsesMinimumAllowance() {
        // "호우"(2자)에 배수만 적용하면 12자 제한이라 "Heavy rain"(10자)은 통과해도
        // 조금만 길어지면 정상 번역이 걸린다. 하한이 이를 구제한다.
        assertThat(OpenAiTranslationClient.isSuspiciouslyLong("호우", "Heavy rain")).isFalse();
        assertThat(OpenAiTranslationClient.isSuspiciouslyLong("폭염", "Cảnh báo nắng nóng")).isFalse();
    }

    @Test
    @DisplayName("하한 경계 — 원문이 짧으면 60자까지 정상, 61자부터 비정상")
    void minimumAllowanceBoundaryIs60Characters() {
        String shortSource = "호우";
        assertThat(OpenAiTranslationClient.isSuspiciouslyLong(shortSource, "a".repeat(60))).isFalse();
        assertThat(OpenAiTranslationClient.isSuspiciouslyLong(shortSource, "a".repeat(61))).isTrue();
    }

    @Test
    @DisplayName("배수 경계 — 원문 40자 기준 240자(6배)까지 정상, 241자부터 비정상")
    void maxLengthRatioBoundaryIs6Times() {
        // 하한(60자)보다 배수 제한이 커지는 길이로 원문을 잡아야 배수 경계가 실제로 검증된다.
        // 이 테스트가 MAX_LENGTH_RATIO 값 자체를 고정한다 — 없으면 6을 5로 바꿔도 아무도 못 잡는다.
        String source = "가".repeat(40);
        assertThat(OpenAiTranslationClient.isSuspiciouslyLong(source, "a".repeat(240))).isFalse();
        assertThat(OpenAiTranslationClient.isSuspiciouslyLong(source, "a".repeat(241))).isTrue();
    }

    @Test
    @DisplayName("실제 재난문자의 영어 번역(약 2.5배)은 정상으로 본다")
    void realWorldEnglishTranslationIsAccepted() {
        String source = "금일16시 경주동부(감포, 문무대왕, 양남면) 호우주의보 발효. 해안가, 야영장, "
                + "산사태취약지역 등 위험지역 접근을 피하시고 안전에 유의바랍니다. [경주시]";
        String translated = "Heavy rain advisory in effect for eastern Gyeongju (Gampo, Munmudaewang, "
                + "Yangnam-myeon) from 16:00 today. Please avoid approaching hazardous areas such as "
                + "coastlines, campsites, and landslide-vulnerable areas, and stay safe. [Gyeongju City]";

        assertThat(OpenAiTranslationClient.isSuspiciouslyLong(source, translated)).isFalse();
    }

    @Test
    @DisplayName("라틴 문자 계열의 팽창(원문의 4.75배)도 정상으로 본다")
    void latinScriptExpansionIsAccepted() {
        // 베트남어는 한글 1음절을 공백 포함 3~7자로 풀어 쓴다. 영어가 이미 2.5배인데 여기에
        // 성조 부호와 다음절 표기가 더해지면 4배를 넘길 수 있어, 가드가 4배면 정상 번역이 버려진다.
        //
        // 태국어(TH)는 공백 없는 알파시라빅 문자라 팽창 양상이 VI 와 다른데, 실측치를 확보하지
        // 못했다 — 여기서는 근거 없는 숫자를 넣지 않고, OpenAiTranslationClientRealApiTest
        // 의 th 케이스(실제 OpenAI 호출)로만 검증한다. 그 통합 테스트는 OPENAI_API_KEY 가 있어야
        // 돌기 때문에, 키가 없는 환경에서는 TH 회귀가 걸리지 않는다는 한계가 있다.
        String source = "가".repeat(40);
        String translated = "a".repeat(190); // 4.75배

        assertThat(OpenAiTranslationClient.isSuspiciouslyLong(source, translated)).isFalse();
    }

    @Test
    @DisplayName("설명이 덧붙은 응답(원문의 12배 이상)은 비정상으로 본다")
    void responseWithAppendedExplanationIsRejected() {
        String source = "가".repeat(40);
        String translated = "a".repeat(500); // 12.5배

        assertThat(OpenAiTranslationClient.isSuspiciouslyLong(source, translated)).isTrue();
    }

    @Test
    @DisplayName("null 입력은 길이 판정 대상이 아니다 (호출부에서 이미 걸러짐)")
    void nullInputIsNotTreatedAsSuspicious() {
        assertThat(OpenAiTranslationClient.isSuspiciouslyLong(null, "anything")).isFalse();
        assertThat(OpenAiTranslationClient.isSuspiciouslyLong("호우", null)).isFalse();
    }
}
