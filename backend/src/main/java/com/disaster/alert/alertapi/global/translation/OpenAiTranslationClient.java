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

    /**
     * 번역만 {@code gpt-4o} 를 쓴다(임베딩·LLM 판정은 계속 {@code gpt-4o-mini}).
     *
     * <p>{@code gpt-4o-mini} 로는 한국어 지명의 태국어 음차가 안정적이지 않았다. {@code [태안군]}
     * 이 {@code [เทศบาลตำบลแท안]} 로 — "태"는 태국 문자로 옮기고 "안"만 한글로 남는 부분 실패가
     * 재현됐다. 프롬프트로 세 번 시도했으나(규칙 강화 → 로마자 경유 2단계 음차 → 예시 분리)
     * 해결되지 않았다. 특히 프롬프트에 {@code 태안 → Taean} 을 예시로 넣었을 때만 통과하고
     * 예시에서 빼면 글자 하나까지 동일하게 실패해, 규칙이 일반화되지 않고 예시를 암기할 뿐임이
     * 확인됐다. 지시 이해가 아니라 모델의 음차 능력 문제로 판단해 모델을 올린다.
     *
     * <p>번역 대상은 한 알림당 본문·유형 2필드 × 5개 언어이며 결과를 DB 에 캐시하므로 같은 원문을
     * 다시 호출하지 않는다. 요금이 문제가 되면 "mini 로 먼저 호출하고 한글이 남았을 때만 상위
     * 모델로 1회 재시도"하는 방식으로 좁힐 수 있다 — 한글 잔존은 정규식으로 정확히 판별 가능하다.
     */
    private static final String MODEL = "gpt-4o";

    /**
     * 번역은 창작이 아니므로 모델에 결정성을 요청한다.
     *
     * <p>다만 이것이 출력 재현성을 <b>보장하지는 않는다</b> — {@code temperature 0} 에서도 같은
     * 원문이 다른 번역을 낼 수 있다. 같은 원문에 같은 번역이 보이는 것은 결과를 DB
     * ({@code disaster_alert_translation} 등)에 캐시해 같은 키를 다시 호출하지 않기 때문이다.
     */
    private static final double TEMPERATURE = 0.0;

    /**
     * 번역문이 원문 길이의 이 배수를 넘으면 모델이 설명·주석을 덧붙인 것으로 보고 버린다.
     * 재난문자는 안전 정보라, 원문에 없는 내용이 섞이는 것이 번역 실패보다 위험하다.
     *
     * <p>배수를 넉넉히(6배) 잡은 이유는 {@link #isSuspiciouslyLong} 주석 참고 — 라틴 문자 계열
     * (특히 VI)의 정상 팽창을 잘못 걸러내면 그 언어 사용자에게만 조용히 원문이 노출된다.
     * 설명이 덧붙은 응답은 보통 10배 이상이라 6배로도 걸러진다.
     */
    private static final int MAX_LENGTH_RATIO = 6;

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
        if (isSuspiciouslyLong(text, result)) {
            throw new IllegalStateException(
                    "번역문이 원문 대비 비정상적으로 김(설명 혼입 의심): targetLang=" + targetLang
                            + ", 원문=" + text.length() + "자, 번역=" + result.length() + "자");
        }
        return result;
    }

    /**
     * 번역문이 원문 대비 비정상적으로 길어 설명·주석이 섞였다고 볼 수 있는지.
     *
     * <p>외부 의존성 없는 순수 판정이라 분리해 둔다 — 언어별 팽창률 경계는 네트워크 호출 없이
     * 단위 테스트로 검증한다.
     *
     * <p>한글 1음절이 목표 언어에서 몇 글자가 되는지는 언어마다 크게 다르다. 한자·간지를 쓰는
     * ZH/JA 는 원문보다 짧아지기도 하지만, 라틴 문자 계열은 확실히 길어진다(실측: 한국어 95자
     * 재난문자 → 영어 약 235자, 2.5배). 베트남어는 음절을 공백 포함 3~7자로 풀어 쓰기 때문에
     * 영어보다 더 팽창한다. 그래서 배수를 넉넉히 잡는다 — 정상 번역을 잘못 버리면 그 언어
     * 사용자에게만 조용히 원문(한국어)이 노출돼 알아채기 어렵기 때문이다.
     *
     * <p><b>한계</b>: EN 2.5배는 실측, VI 는 표기 방식에서 온 추정이고, TH(공백 없는 알파시라빅)와
     * "이상 응답은 보통 10배 이상"은 실측 근거가 없는 가정이다. TH 는 {@code
     * OpenAiTranslationClientRealApiTest} 의 실제 OpenAI 호출로만 검증되는데,
     * 그 테스트는 OPENAI_API_KEY 가 있어야 돌아 키 없는 환경에서는 회귀가 걸리지 않는다.
     * 실측치가 쌓이면 배수를 다시 조정할 것.
     */
    static boolean isSuspiciouslyLong(String source, String translated) {
        if (source == null || translated == null) {
            return false;
        }
        int limit = Math.max(MIN_LENGTH_ALLOWANCE, source.length() * MAX_LENGTH_RATIO);
        return translated.length() > limit;
    }

    /**
     * 지명 관련 규칙은 한 덩어리로 묶어 "한글을 남기지 않는다"까지 못박는다. 규칙이 "안의 지명만
     * 번역한다"와 "통용 표기가 없으면 음차한다"로 흩어져 있을 때, 통용 표기가 거의 없는 언어(TH 등)
     * 에서 모델이 "옮길 표기가 없으니 원문 유지"로 해석해 대괄호 안 한글을 그대로 남기는 사례가
     * 실측됐다(TH 5건 중 1건 `[태안군]`). 마지막 줄은 개별 규칙이 아니라 출력 전체에 대한 불변 조건이다.
     *
     * <p>"한 음절이라도 한글로 남겨서는 안 된다"는 {@code [태안군]} → {@code [เทศบาลตำบลแท안]}
     * 처럼 앞 음절만 옮기고 뒤 음절을 한글로 남기는 <b>부분 실패</b>를 겨냥한 문구다.
     *
     * <p><b>음차 방법을 프롬프트로 지시하지 않는다.</b> 한때 "먼저 로마자로 옮긴 뒤 그 발음을 대상
     * 언어 문자로 표기하라"는 2단계 지시를 넣었으나, 프롬프트에 {@code 태안 → Taean} 을 예시로
     * 함께 준 경우에만 통과하고 예시에서 빼면 글자 하나까지 동일하게 실패했다 — 규칙이 일반화되지
     * 않고 예시를 암기할 뿐이었다. 근거 없는 지시를 남겨두면 다른 언어의 고유 음역 관행(JA 가나
     * 음독, ZH 한자음 대응)까지 영어 발음 경유로 왜곡할 위험만 있어 걷어냈다. 음차 품질은 프롬프트가
     * 아니라 모델 능력의 문제이므로 {@link #MODEL} 상향으로 해결한다.
     *
     * <p>예시로 드는 지명({@code [경주시]})은 {@code OpenAiTranslationClientRealApiTest} 의 고정
     * 원문({@code [태안군]})과 겹치지 않게 유지한다. 겹치면 테스트가 통과해도 "규칙이 임의의 지명에
     * 일반화된다"가 아니라 "예시로 준 지명을 재현한다"만 검증하게 된다.
     *
     * <p>포맷 인자는 위치 지정자(`%1$s`=언어명, `%2$s`=원문)로 명시한다 — 일반 `%s`와 섞으면
     * 규칙을 추가·삭제할 때 인자 순서가 조용히 어긋난다.
     */
    private String buildPrompt(String text, String languageName) {
        return """
                너는 한국 재난안전문자 전문 번역기다. 아래 원문을 %1$s로 번역해라.

                규칙:
                - 번역문만 출력한다. 설명·따옴표·머리말·번역 노트를 붙이지 않는다.
                - 원문에 없는 내용을 추가하거나 추측해서 보충하지 않는다.
                - ▲ · ※ 등 기호와 줄바꿈·문장 구조를 원문 그대로 보존한다.
                - 대괄호로 감싼 발신 기관 표기(예: [경주시])는 대괄호를 유지한 채 안의 지명을 %1$s로 옮긴다.
                - 지명·기관명은 %1$s의 통용 표기를 쓰고, 통용 표기가 없으면 음차한다.
                  한글을 그대로 두거나 한 음절이라도 한글로 남겨서는 안 된다.
                - "발효/해제/특보/주의보/경보"는 기상·재난 경보 용어로 해석한다(발효 = 경보가 유효함).
                - 붙여 쓴 행정 용어(예: 산사태취약지역, 호우주의보)를 하나의 용어로 인식해 번역한다.
                - 최종 출력에 한글이 한 글자도 남아서는 안 된다.

                원문:
                %2$s
                """.formatted(languageName, text);
    }
}
