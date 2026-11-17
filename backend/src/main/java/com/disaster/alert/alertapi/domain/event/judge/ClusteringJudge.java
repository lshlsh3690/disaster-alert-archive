package com.disaster.alert.alertapi.domain.event.judge;

import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.AlertFacts;
import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.PairFacts;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 두 사건 문자가 같은 사건인지 LLM 이 판정하는 판사. Red 단계 뼈대 — 구현은 Green 에서.
 *
 * <p>일일 채점 스케줄러와 평가 도구의 일치율 측정 모드가 같은 이 코드를 쓴다(설계: specs/006 plan 3절).
 * 프롬프트 조립({@link #buildPrompt})과 응답 해석({@link #parseResponse})은 DB·Spring 없이 테스트하도록 순수 static 으로 둔다.
 *
 * <p><b>시스템 설정값(시군구 키·72시간·임계값)과 시스템 배정(이벤트 id)은 입력에 넣지 않는다</b> — 판사가 시스템을 닮아
 * 점수가 저절로 오르는 것을 막기 위해서다. {@link JudgeInput} 에 이벤트 관련 필드가 아예 없는 구조로 이를 보장한다.
 */
// Red 단계 뼈대 — 구현은 Green 에서
@Component
public class ClusteringJudge {

    /** 프롬프트 본문이 바뀌면 올린다. 버전마다 272쌍 일치율이 있어야 채점한다. */
    public static final String PROMPT_VERSION = "v1";

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
    public record JudgeResult(JudgeVerdict verdict, int promptTokens, int completionTokens) {
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

    /** 판사를 호출한다. 뼈대는 호출하지 않고 UNSURE 를 돌려준다. */
    public JudgeResult judge(JudgeInput input) {
        return new JudgeResult(JudgeVerdict.UNSURE, 0, 0);
    }

    /** 판사에게 보낼 프롬프트를 만든다. 뼈대는 빈 문자열이다. */
    static String buildPrompt(JudgeInput input) {
        return "";
    }

    /** LLM 응답을 판정으로 해석한다. 뼈대는 항상 UNSURE 다. */
    static JudgeVerdict parseResponse(String response) {
        return JudgeVerdict.UNSURE;
    }

    /** 프롬프트 버전의 일치율. 뼈대는 항상 비어 있다(= 채점하지 않음). */
    static Optional<Agreement> agreementOf(String promptVersion) {
        return Optional.empty();
    }
}
