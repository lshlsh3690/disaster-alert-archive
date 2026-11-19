package com.disaster.alert.alertapi.domain.event.judge;

import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.AgencyKind;
import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.AlertFacts;
import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.PairFacts;
import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.SigunguRelation;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 두 사건 문자가 같은 사건인지 LLM 이 판정하는 판사.
 *
 * <p>일일 채점 스케줄러와 평가 도구의 일치율 측정 모드가 같은 이 코드를 쓴다(설계: specs/006 plan 3절).
 * 프롬프트 조립({@link #buildPrompt})과 응답 해석({@link #parseResponse})은 DB·Spring 없이 테스트하도록 순수 static 으로 둔다.
 *
 * <p><b>시스템 설정값(시군구 키·72시간·임계값)과 시스템 배정(이벤트 id)은 입력에 넣지 않는다</b> — 판사가 시스템을 닮아
 * 점수가 저절로 오르는 것을 막기 위해서다. {@link JudgeInput} 에 이벤트 관련 필드가 아예 없는 구조로 이를 보장한다.
 */
@Component
public class ClusteringJudge {

    /**
     * 프롬프트 본문이나 모델이 바뀌면 올린다(일치율은 프롬프트와 모델을 함께 잰 값이다). 버전마다 272쌍 일치율이
     * {@link JudgeVersionCatalog} 에 있어야 일일 채점을 한다. 올리는 순서: 이 값과 {@link #MODEL} 을 바꾼다 →
     * {@code --eval.judge-agreement} 로 272쌍을 잰다(카탈로그에 아직 없어도 측정은 돈다) → 잰 값을 카탈로그 맨 앞에 추가한다.
     * 카탈로그에 없는 동안 일일 채점은 warn 을 남기고 건너뛴다.
     */
    public static final String PROMPT_VERSION = "v2";

    // 모델은 상수로 둔다 — 카탈로그에서 꺼내면 새 버전을 카탈로그에 넣기 전(=일치율을 재기 전)에는 판사가 뜨지 않아
    // 측정 자체를 할 수 없다. 카탈로그 항목의 모델과 같은지는 테스트(ClusteringJudgeTest)가 검사한다.
    // 모델을 고른 이유와 버전별 수치는 카탈로그 javadoc 에 있다.
    public static final String MODEL = "gpt-4o-mini";
    // 같은 쌍은 같은 답이 나와야 프롬프트 버전끼리 점수를 비교할 수 있어 온도 0.
    private static final double TEMPERATURE = 0.0;
    // 답은 SAME/DIFF/UNSURE 한 단어다. 이유를 덧붙이려 해도 잘리게 막아 비용을 줄인다(잘린 응답은 형식 실패로 센다).
    private static final int MAX_TOKENS = 5;

    /**
     * 기준 문서(CLUSTERING_CRITERIA.md 1절)의 원칙·경계 사례·적용 순서를 옮긴 지시문. few-shot 은 두지 않는다.
     * 기준을 바꾸면 이 문장을 고치고 {@link #PROMPT_VERSION} 을 올린다.
     */
    private static final String INSTRUCTIONS = """
            너는 한국 재난문자 두 건이 "같은 사건"인지 판정하는 판사다. 아래 기준만 따른다.

            [같은 사건의 정의]
            1. 같은 지역에서 하나의 사고·사건이 이어지는 경우(발생, 통제·대피, 복구·해제 등 후속 문자)는 같은 사건이다.
            2. 인접 지역이고 같은 개체·현상이 이어진 경우(산불·사고 확산, 동물 이동 등)는 같은 사건이다. 문자에 연속성 단서가 있어야 한다. 단 산불 확산은 아래 B 를 따른다.
            3. 별개의 사고·사건(각각 다른 장소·번지의 화재, 서로 다른 지점의 시설 파손, 같은 날 따로 발생한 사고 등)은 다른 사건이다.

            [경계 사례 - 위 1~3 이 부딪칠 때 여기가 우선한다]
            A. 호우: 같은 호우(하나의 강우 사태, 복구·해제 공지까지) 동안 같은 시군구에서 서로 다른 지점에 난 침수·범람·도로 통제·통행 해제·대피는 같은 사건이다. 원인이 같은 호우면 "서로 다른 지점의 시설 파손은 다른 사건"이라는 3번은 적용하지 않고, 유형이 교통통제여도 같다. 이웃 시군구는 같은 호우라도 다른 사건이다.
            B. 산불 확산: 큰 산불이 번지며 여러 시군구·읍면동에 차례로 나온 대피명령·통제는 "의성 산불 확산" 같은 이어짐 표시가 없어도 같은 사건이다. 2번의 연속성 단서 요구는 산불 확산에는 적용하지 않는다.
            C. 산불 별개 발화: 같은 시군구·같은 날이어도 서로 다른 곳에서 각각 "발생"을 알리는 산불은 다른 사건이다. 문자가 새 발생을 알리면 다른 사건, 번짐·접근·대피를 알리면 B 에 따라 같은 사건이다.
            D. 여러 시군구로 보낸 문자: 문자 끝 발신 기관([..])의 시군구 문자로 본다.
            E. 행정구역이 바뀐 지역(인천 옛 서구·중구·동구 등): 화면의 새 구 이름이 아니라 발송 당시의 발신 기관(옛 구청) 기준으로 같은 시군구인지 본다.
            F. "해제"는 사건의 끝이 아니다: 통제·대피명령 해제 뒤에 같은 사건이 다시 이어지는 일이 흔하다. 해제 문자가 있다고 사건을 끊지 않는다. 사건을 가르는 것은 해제 여부가 아니라 A·C 등 원래 기준이다.
            G. 이름이 같은 한 시설: 같은 다리·하천(산책로·하상도로 포함) 등 이름이 같은 한 시설을 같은 비 동안 통제·해제한 문자는 보낸 시군구가 달라도 같은 사건이다(A·D 의 예외). 두 문자가 모두 그 시설을 알릴 때만 쓰고(한쪽만 그 시설을 말하면 G 를 쓰지 않는다), 비가 그친 뒤 다른 비에 같은 시설을 다시 통제하면 다른 사건이다.
            I. 시군구가 아닌 발신 기관(고속도로·철도·국립공원·도청·관리본부 등): 발신 기관이 아니라 문자에 적힌 장소가 있는 시군구의 문자로 보고 A·G 를 적용한다. D 는 발신 기관이 시군구일 때만 쓴다.

            [적용 순서 - 앞 단계에서 답이 나면 뒤 단계는 보지 않는다]
            1. 어느 시군구 문자인지 정한다: 발신 기관이 시군구면 그 시군구(D), 행정구역이 바뀐 곳은 옛 구청 기준(E), 시군구가 아닌 기관이면 문자 속 장소의 시군구(I).
            2. 장소 규칙: 이름이 같은 한 시설을 같은 비 동안 알린 문자면 시군구가 달라도 같은 사건(G).
            3. 시군구·유형 규칙: 호우는 같은 시군구·같은 비 동안이면 유형이 달라도 같은 사건, 이웃 시군구는 다른 사건(A). 산불은 B·C. 그 밖의 유형은 원칙 1~3(장소·번지가 다르면 다른 사건).
            4. 어느 단계에서도 해제 문자로 사건을 끊지 않는다(F).

            기준끼리 부딪쳐 이 순서로도 답이 갈리지 않을 때만 UNSURE 로 답한다. 본문에 단서가 적다는 이유만으로 UNSURE 를 고르지 말고, 위 기준에 비추어 더 그럴듯한 쪽(SAME 또는 DIFF)을 고른다.
            """;

    private static final String ANSWER_FORMAT = """
            [답변 형식]
            SAME(같은 사건), DIFF(다른 사건), UNSURE(기준끼리 부딪쳐 답이 갈리지 않음) 중 정확히 한 단어만 답하라. 이유·설명·문장부호를 덧붙이지 마라.
            """;

    /**
     * 판사 입력 쪽 문자 하나.
     *
     * @param receivedRegionNames 수신 지역 이름(사람이 읽는 이름)
     * @param facts               {@code AlertPlaceFacts} 가 규칙으로 계산한 사실(판정이 아니다)
     */
    public record JudgeAlert(LocalDateTime sentAt, String disasterType, List<String> receivedRegionNames,
                             String body, AlertFacts facts) {
    }

    /** 판사 입력. 두 문자와 둘을 견준 사실뿐이다 — 이벤트 id 등 시스템 배정은 필드로 두지 않는다. */
    public record JudgeInput(JudgeAlert first, JudgeAlert second, PairFacts pairFacts) {
    }

    /** 판정과 비용 확인용 토큰 수. */
    public record JudgeResult(JudgeVerdict verdict, int promptTokens, int completionTokens, String rawResponse) {
        /** 원응답을 남기지 않는 판정(테스트·집계용). */
        public JudgeResult(JudgeVerdict verdict, int promptTokens, int completionTokens) {
            this(verdict, promptTokens, completionTokens, null);
        }
    }

    /**
     * 한 프롬프트 버전의 272쌍 일치율(같은 사건 쌍·다른 사건 쌍 각각 분자/분모).
     */
    public record Agreement(int sameNum, int sameDen, int diffNum, int diffDen) {
    }

    private final ChatClient chatClient;

    /** Spring AI 1.0 은 {@code ChatClient} 빈을 자동 생성하지 않아 Builder 를 받아 빌드한다({@code OpenAiTranslationClient} 와 동일). */
    public ClusteringJudge(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 판사를 호출해 판정과 토큰 수를 돌려준다. 일일 채점과 일치율 측정이 쌍마다 부른다.
     *
     * <p>호출 자체의 실패(네트워크·키 오류)는 삼키지 않고 그대로 던진다 — FORMAT_FAILURE 로 바꾸면 모델 형식 문제와
     * 호출 장애가 섞이므로, 어떻게 셀지는 호출한 쪽이 정한다.
     */
    public JudgeResult judge(JudgeInput input) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(MODEL).temperature(TEMPERATURE).maxTokens(MAX_TOKENS).build();

        // 1) 토큰 수(usage)는 content() 로는 못 얻어 chatResponse() 로 받는다
        ChatResponse response = chatClient.prompt().options(options).user(buildPrompt(input)).call().chatResponse();
        if (response == null || response.getResult() == null) {
            // 2) 응답 본문이 없으면 한 단어를 받지 못한 것이다 — 판사가 모른다(UNSURE)가 아니라 형식 실패로 센다
            return new JudgeResult(JudgeVerdict.FORMAT_FAILURE, 0, 0);
        }

        // 3) 정확히 한 단어일 때만 판정으로 받는다. 원응답은 형식 실패가 무엇이었는지 리포트에서 보려고 같이 넘긴다
        String raw = response.getResult().getOutput().getText();
        JudgeVerdict verdict = parseResponse(raw);

        // 4) usage 가 비어 오는 응답도 있어 없으면 0 으로 둔다(비용 집계만 빠질 뿐 판정은 유효)
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        int promptTokens = usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
        int completionTokens = usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
        return new JudgeResult(verdict, promptTokens, completionTokens, raw);
    }

    /**
     * 판사에게 보낼 프롬프트를 만든다: 기준 지시문 + 두 문자 + 규칙으로 계산한 사실 + 답변 형식.
     *
     * <p>시스템 설정값(시군구 키·72시간·임계값)과 시스템 배정(이벤트 id)은 넣지 않는다 — 입력 타입에 그런 필드가 없다.
     */
    static String buildPrompt(JudgeInput input) {
        StringBuilder sb = new StringBuilder(INSTRUCTIONS);

        // 1) 두 문자는 발송 시각·유형·수신 지역·본문을 그대로 보여 준다(판단의 재료)
        appendAlert(sb, "문자 1", input.first());
        appendAlert(sb, "문자 2", input.second());

        // 2) 사실 블록: 코드가 규칙으로 센 값임을 밝힌다 — 판사가 사실을 정답으로 오해하지 않게
        sb.append("\n[사실 - 규칙으로 계산한 값이며 판정이 아니다]\n");
        appendFacts(sb, "문자 1", input.first().facts());
        appendFacts(sb, "문자 2", input.second().facts());
        PairFacts pair = input.pairFacts();
        sb.append("- 두 문자의 시군구 관계: ").append(relationLabel(pair.relation())).append('\n');
        sb.append("- 발송 간격: ").append(pair.gap().toHours()).append("시간 ")
                .append(pair.gap().toMinutesPart()).append("분\n");
        sb.append("- 두 본문에 같이 나오는 시설 이름 후보: ")
                .append(pair.sharedFacilityNames().isEmpty() ? "없음" : String.join(", ", pair.sharedFacilityNames()))
                .append(" (같은 시설인지는 본문으로 판단하라)\n");

        // 3) 답변 형식은 맨 끝에 둔다 — parseResponse 가 한 단어만 받는다
        return sb.append('\n').append(ANSWER_FORMAT).toString();
    }

    private static void appendAlert(StringBuilder sb, String label, JudgeAlert alert) {
        sb.append('\n').append('[').append(label).append("]\n")
                .append("- 발송 시각: ").append(alert.sentAt()).append('\n')
                .append("- 유형: ").append(alert.disasterType() == null ? "표시 없음" : alert.disasterType()).append('\n')
                .append("- 수신 지역: ").append(String.join(", ", alert.receivedRegionNames())).append('\n')
                .append("- 본문: ").append(alert.body()).append('\n');
    }

    private static void appendFacts(StringBuilder sb, String label, AlertFacts facts) {
        String agency = facts.senderAgency()
                .map(name -> name + " (" + agencyLabel(facts.agencyKind()) + ")")
                .orElse("표시 없음");
        // 코드는 비교용이다 — 판사가 숫자로 지명을 지어내지 않게 이름은 발신 기관 칸을 보라고 밝힌다
        String sigungu = facts.sigunguId()
                .map(id -> "시군구 식별자 " + id + " (비교용 코드, 이름은 발신 기관 칸 참고)")
                .orElseGet(() -> unknownSigunguLabel(facts.agencyKind()));
        sb.append("- ").append(label).append(" 발신 기관: ").append(agency).append('\n')
                .append("- ").append(label).append(" 시군구: ").append(sigungu)
                .append(" / 수신 시군구 ").append(facts.receivedSigunguCount()).append("곳\n");
    }

    /**
     * 시군구를 규칙으로 못 정한 문자의 표시. 이유에 따라 판사가 따를 기준이 달라 문구를 나눈다 —
     * 시군구가 아닌 기관은 본문 속 장소로 정하고(기준 I), 시군구 기관인데 같은 이름이 여러 시도에 있어 못 정한
     * 경우까지 I 를 지시하면 D 와 동시에 지시하게 된다.
     */
    private static String unknownSigunguLabel(AgencyKind kind) {
        return switch (kind) {
            case NON_SIGUNGU -> "모름 (시군구가 아닌 기관이라 본문에 적힌 장소의 시군구로 판단하라, 기준 I)";
            case SIGUNGU -> "모름 (시군구 기관이지만 같은 이름이 여러 시도에 있어 하나로 정하지 못함)";
            case NONE -> "모름 (발신 기관 표시가 없음)";
        };
    }

    // Map 대신 switch — enum 값이 늘면 프롬프트에 "null" 이 조용히 찍히지 않고 컴파일 오류로 드러난다
    private static String agencyLabel(AgencyKind kind) {
        return switch (kind) {
            case SIGUNGU -> "시군구 기관";
            case NON_SIGUNGU -> "시군구가 아닌 기관";
            case NONE -> "기관 표시 없음";
        };
    }

    private static String relationLabel(SigunguRelation relation) {
        return switch (relation) {
            case SAME -> "같은 시군구";
            case ADJACENT -> "이웃한 시군구";
            case OTHER -> "서로 먼 시군구";
            case UNKNOWN -> "모름 (한쪽 이상의 시군구를 규칙으로 정하지 못함)";
        };
    }

    /**
     * LLM 응답을 판정으로 해석한다. 앞뒤 공백만 걷고 정확히 SAME/DIFF/UNSURE 한 단어일 때만 받는다.
     * 그 밖(이유가 덧붙음·소문자·빈 응답·null)은 UNSURE 로 바꾸지 않고 FORMAT_FAILURE 로 돌려준다 —
     * 앞 단어만 떼어 읽으면 판사가 형식을 어기는 정도를 점수가 가린다.
     */
    static JudgeVerdict parseResponse(String response) {
        if (response == null) {
            return JudgeVerdict.FORMAT_FAILURE;
        }
        return switch (response.trim()) {
            case "SAME" -> JudgeVerdict.SAME;
            case "DIFF" -> JudgeVerdict.DIFF;
            case "UNSURE" -> JudgeVerdict.UNSURE;
            default -> JudgeVerdict.FORMAT_FAILURE;
        };
    }

    /**
     * 프롬프트 버전의 272쌍 일치율. 값이 없는 버전은 비어 있다(= 일일 채점을 하지 않음).
     * 값과 측정 근거는 {@link JudgeVersionCatalog} 에 있고, 여기서는 호출부가 쓰던 {@link Agreement} 모양으로만 바꿔 준다.
     */
    public static Optional<Agreement> agreementOf(String promptVersion) {
        return JudgeVersionCatalog.find(promptVersion)
                .map(e -> new Agreement(e.sameNum(), e.sameDen(), e.diffNum(), e.diffDen()));
    }
}
