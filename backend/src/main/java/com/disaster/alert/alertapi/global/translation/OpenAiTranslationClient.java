package com.disaster.alert.alertapi.global.translation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 재난문자 본문/유형·이벤트 제목 번역 클라이언트 (OpenAI).
 *
 * <p>DeepL(Free)을 대체한다. DeepL Free 는 계정당 <b>평생 누적</b> 한도라 소진되면 리셋되지 않아
 * 예비 키까지 모두 막히면 번역이 영구적으로 멈춘다(실제로 운영에서 두 키가 모두 456 Quota
 * exceeded 로 막혔다). 또 번역 지시를 줄 수 없어 재난문자 특유의 표기 — 붙여 쓴 행정 용어
 * ("산사태취약지역"), 경보 용어의 다의어("발효"), ▲·· 기호 불릿, 대괄호 발신 기관 표기 — 를
 * 다루기 어려웠다. 프롬프트로 이 규칙들을 직접 규정할 수 있는 LLM 으로 옮긴다.
 *
 * <p><b>임베딩·클러스터링과는 무관하다.</b> {@code EventClusteringService} 는 한국어 원문
 * ({@code disaster_alert.message})만 임베딩하므로, 번역 엔진 교체가 벡터·코사인 유사도·이벤트
 * 클러스터링·위험도 점수에 영향을 주지 않는다.
 *
 * <p>{@code ChatClient.Builder} 를 주입받아 빌드하는 방식은 {@code LlmRiskProfiler} 와 동일하다
 * (Spring AI 1.0 은 {@code ChatClient} 빈을 자동 생성하지 않는다).
 */
@Slf4j
@Component
public class OpenAiTranslationClient {

    private static final String MODEL = "gpt-4o-mini";

    /** 번역은 창작이 아니므로 결정적으로 — 같은 원문이면 항상 같은 번역이 나오게 한다. */
    private static final double TEMPERATURE = 0.0;

    /**
     * 번역문이 원문 길이의 이 배수를 넘으면 모델이 설명·주석을 덧붙인 것으로 보고 버린다.
     * 재난문자는 안전 정보라, 원문에 없는 내용이 섞이는 것이 번역 실패보다 위험하다.
     */
    private static final int MAX_LENGTH_RATIO = 4;

    /**
     * 길이 가드의 하한. "호우" 같은 짧은 재난 유형은 배수만 적용하면 정상 번역
     * ("Heavy rain")도 걸리므로, 이 길이까지는 무조건 허용한다.
     */
    private static final int MIN_LENGTH_ALLOWANCE = 60;

    private static final Map<String, String> LANGUAGE_NAMES = Map.of(
            "EN", "영어",
            "JA", "일본어",
            "ZH", "중국어 간체",
            "VI", "베트남어",
            "TH", "태국어"
    );

    private final ChatClient chatClient;

    public OpenAiTranslationClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * @param targetLang {@link SupportedLanguage#getDbCode()} 대문자 코드 (예: "JA")
     * @return 번역문. 실패 시 예외를 던져 호출 측이 스킵하고 원문으로 폴백하도록 한다.
     */
    public String translate(String text, String targetLang) {
        String languageName = LANGUAGE_NAMES.get(targetLang);
        if (languageName == null) {
            throw new IllegalArgumentException("지원하지 않는 번역 대상 언어: " + targetLang);
        }

        String translated = chatClient.prompt()
                .options(OpenAiChatOptions.builder().model(MODEL).temperature(TEMPERATURE).build())
                .user(buildPrompt(text, languageName))
                .call()
                .content();

        if (translated == null || translated.isBlank()) {
            throw new IllegalStateException("번역 결과가 비어 있음: targetLang=" + targetLang);
        }

        String result = translated.trim();
        int limit = Math.max(MIN_LENGTH_ALLOWANCE, text.length() * MAX_LENGTH_RATIO);
        if (result.length() > limit) {
            throw new IllegalStateException(
                    "번역문이 원문 대비 비정상적으로 김(설명 혼입 의심): targetLang=" + targetLang
                            + ", 원문=" + text.length() + "자, 번역=" + result.length() + "자");
        }
        return result;
    }

    private String buildPrompt(String text, String languageName) {
        return """
                너는 한국 재난안전문자 전문 번역기다. 아래 원문을 %s로 번역해라.

                규칙:
                - 번역문만 출력한다. 설명·따옴표·머리말·번역 노트를 붙이지 않는다.
                - 원문에 없는 내용을 추가하거나 추측해서 보충하지 않는다.
                - ▲ · ※ 등 기호와 줄바꿈·문장 구조를 원문 그대로 보존한다.
                - 대괄호로 감싼 발신 기관 표기(예: [경주시])는 대괄호를 유지한 채 안의 지명만 번역한다.
                - "발효/해제/특보/주의보/경보"는 기상·재난 경보 용어로 해석한다(발효 = 경보가 유효함).
                - 붙여 쓴 행정 용어(예: 산사태취약지역, 호우주의보)를 하나의 용어로 인식해 번역한다.
                - 지명·기관명은 해당 언어의 통용 표기를 쓰고, 없으면 음차한다.

                원문:
                %s
                """.formatted(languageName, text);
    }
}
