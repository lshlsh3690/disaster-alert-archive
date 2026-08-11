package com.disaster.alert.alertapi.global.translation;

import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 OpenAI 를 호출해 프롬프트 규칙(spec.md FR-014)이 지켜지는지 검증한다.
 *
 * <p><b>고정 원문을 쓴다.</b> 이전 버전({@code DisasterAlertServiceOpenAiTranslationTest})은
 * {@code getLatestAlert(5, lang)} 로 그때그때의 최신 재난문자를 번역해서, 어떤 알림이 목록에
 * 들어오느냐에 따라 결과가 달라지는 비결정적 테스트였다 — 실제로 당시 지원하던 TH 실행 시
 * 하필 {@code [태안군]} 이 포함되어 실패했다. 여기서는 검증하려는 규칙을 모두 담은 원문 하나를
 * 상수로 고정해, 실패하면 항상 같은 이유로 실패하게 한다.
 *
 * <p><b>이 단정들은 기계적일 뿐 번역이 말이 되는지는 보지 않는다.</b> 한글 잔존·기호 개수·대괄호
 * 개수는 모두 통과하면서 내용이 틀릴 수 있다 — 실제로 다른 엔진 비교 측정에서 {@code 폭염}을
 * {@code 爆炎}(폭발하는 불꽃)으로, {@code 발효}를 {@code 発効}(조약 발효)로 옮긴 출력이 아래
 * 검사를 전부 통과했다. 용어 수준 단정은 후속 작업으로 추가한다.
 *
 * <p>OPENAI_API_KEY 가 없거나 유효하지 않으면 이 테스트는 스킵되지 않고 그대로 실패한다.
 * 그래서 {@code @Tag("realApi")} 로 기본 {@code test} 태스크에서 제외한다 — 키가 없는 환경에서도
 * 나머지 테스트는 온전히 돌아야 하기 때문이다. 실행은 {@code ./gradlew realApiTest} 또는 IDE 에서
 * 이 클래스를 직접 실행한다. 프롬프트 규칙을 바꿨을 때 수동으로 돌려 확인하는 용도다.
 */
@IntegrationTest
@Tag("realApi")
@Slf4j
@DisplayName("OpenAiTranslationClient 실제 API 호출")
class OpenAiTranslationClientRealApiTest {

    /**
     * 검증 대상 규칙을 한 문장에 모두 담은 고정 원문.
     * <ul>
     *   <li>{@code 발효} — 경보 용어(fermentation 아님)</li>
     *   <li>{@code ▲} 3개 — 기호·구조 보존</li>
     *   <li>{@code 산사태취약지역} — 붙여 쓴 행정 용어</li>
     *   <li>{@code [태안군]} — 대괄호 발신 기관. 제거된 TH 에서 한글이 남았던 바로 그 지명</li>
     * </ul>
     *
     * <p>{@code 태안}은 프롬프트에 예시로 등장하는 지명({@code [경주시]})과 <b>일부러 다르게</b>
     * 골랐다. 예시와 검증 대상이 같으면 이 테스트가 통과해도 "규칙이 임의의 지명에 일반화된다"가
     * 아니라 "예시로 준 지명을 재현한다"만 확인하게 된다 — 실제로 프롬프트에 {@code 태안}을 예시로
     * 넣었던 회차에만 TH 가 통과하고 빼면 동일하게 실패해, 이 구분이 필요하다는 것이 실증됐다.
     * TH 는 빠졌지만 이 구분은 남은 언어에도 그대로 유효하므로 원문을 바꾸지 않는다.
     */
    private static final String SOURCE =
            "폭염경보 발효 ▲야외활동 자제 ▲물 충분히 섭취 ▲산사태취약지역 접근 금지 "
                    + "등 건강관리에 유의하시기 바랍니다.[태안군]";

    private static final int EXPECTED_BULLET_COUNT = 3;

    @Autowired
    private OpenAiTranslationClient translationClient;

    @ParameterizedTest(name = "{0}")
    @EnumSource(SupportedLanguage.class)
    @DisplayName("지원 언어 전체에서 한글이 남지 않고 기호·대괄호 구조가 보존된다")
    void translatesWithoutLeavingHangulAndKeepsMarkers(SupportedLanguage language) {
        String translated = translationClient.translate(SOURCE, language.getDbCode());

        log.info("[{}] {}", language.getDbCode(), translated);

        assertThat(translated)
                .as("번역문이 비어 있으면 안 된다")
                .isNotBlank()
                .as("번역문이 원문과 같으면 안 된다")
                .isNotEqualTo(SOURCE);

        assertThat(containsHangul(translated))
                .as("번역문에 한글이 남으면 안 된다(대괄호 안 지명 포함): %s", translated)
                .isFalse();

        // 존재 여부(contains)만 보면 여분·불균형 대괄호를 놓친다 — ▲ 와 같이 개수로 비교한다.
        assertThat(countChar(translated, '['))
                .as("여는 대괄호 개수가 원문과 같아야 한다: %s", translated)
                .isEqualTo(countChar(SOURCE, '['));
        assertThat(countChar(translated, ']'))
                .as("닫는 대괄호 개수가 원문과 같아야 한다: %s", translated)
                .isEqualTo(countChar(SOURCE, ']'));

        assertThat(countChar(translated, '▲'))
                .as("불릿 기호 ▲ 개수가 보존되어야 한다: %s", translated)
                .isEqualTo(EXPECTED_BULLET_COUNT);
    }

    private boolean containsHangul(String text) {
        return text != null && text.matches("(?s).*\\p{IsHangul}.*");
    }

    private long countChar(String text, char target) {
        return text.chars().filter(c -> c == target).count();
    }
}
