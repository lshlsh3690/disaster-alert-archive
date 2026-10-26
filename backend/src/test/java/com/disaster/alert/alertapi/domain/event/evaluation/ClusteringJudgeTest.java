package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClusteringJudgeTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final ClusteringJudge judge = new ClusteringJudge(chatModel);

    // 원문에 연속 공백·줄바꿈을 일부러 넣어 입력 정규화(공백 하나로 줄이기)를 확인할 수 있게 한다
    private final DisasterAlert earlier = DisasterAlert.builder()
            .id(1L).sn(100L).disasterType("산불").originalRegion("경북 안동시")
            .createdAt(LocalDateTime.of(2026, 10, 20, 14, 5))
            .message("안동시 임하면 산불이   발생했습니다.\n주민은  대피하세요").build();
    private final DisasterAlert later = DisasterAlert.builder()
            .id(2L).sn(101L).disasterType("산불").originalRegion("경북 안동시 임하면")
            .createdAt(LocalDateTime.of(2026, 10, 20, 16, 30))
            .message("임하면 산불 진화 중입니다").build();

    private static ChatResponse answer(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private Prompt capturePrompt() {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        return captor.getValue();
    }

    // 결정: 판사는 공유 ChatModel 의 기본 모델(gpt-4o-mini, 클러스터링 폴백과 같음)이 아니라
    // 호출마다 gpt-4o + 온도 0 으로 부른다. 같은 모델이면 폴백과 같은 실수를 같이 한다.
    // Red 단계: 뼈대가 ChatModel 을 아예 부르지 않아 verify 에서 "Wanted but not invoked" 로 실패한다.
    @Test
    @DisplayName("judgePair 는 ChatModel 을 gpt-4o, temperature 0 옵션으로 부른다")
    void callsChatModelWithGpt4oAndZeroTemperature() {
        when(chatModel.call(any(Prompt.class))).thenReturn(answer("SAME"));

        judge.judgePair(earlier, later);

        // 옵션이 OpenAiChatOptions 로 실려야 호출마다 모델을 지정한 것이다(null 이면 공유 기본값으로 간다)
        OpenAiChatOptions options = (OpenAiChatOptions) capturePrompt().getOptions();
        assertThat(options.getModel()).isEqualTo("gpt-4o");
        assertThat(options.getTemperature()).isEqualTo(0.0);
    }

    // 결정: 판사 입력은 두 알림 각각의 유형·수신 지역·발송 시각 + 공백을 줄인 원문이고, 순서는 earlier 먼저.
    // 시각 표기는 고정하지 않으므로 날짜·시분 조각이 들어 있는지만 본다.
    // Red 단계: ChatModel 이 불리지 않아 verify 에서 실패한다.
    @Test
    @DisplayName("judgePair 의 user 입력에 두 알림의 유형·지역·시각·공백 정리된 원문이 earlier 먼저 들어간다")
    void pairInputHasMetadataAndNormalizedTextInOrder() {
        when(chatModel.call(any(Prompt.class))).thenReturn(answer("DIFF"));

        judge.judgePair(earlier, later);

        // 시스템 프롬프트가 아니라 마지막 user 메시지가 두 알림을 담는다
        String user = capturePrompt().getInstructions().stream()
                .filter(UserMessage.class::isInstance)
                .map(m -> ((UserMessage) m).getText())
                .reduce((a, b) -> b).orElseThrow();
        // 줄바꿈·연속 공백이 한 칸으로 줄어야 같은 글이 다른 토큰열이 되지 않는다
        String earlierText = "안동시 임하면 산불이 발생했습니다. 주민은 대피하세요";
        assertThat(user).contains("산불", "경북 안동시", "2026-10-20", "14:05", earlierText);
        assertThat(user).contains("경북 안동시 임하면", "16:30", "임하면 산불 진화 중입니다");
        // 순서가 바뀌면 "나중 알림이 먼저 알림의 후속인가" 라는 판정 기준이 뒤집힌다
        assertThat(user.indexOf(earlierText)).isLessThan(user.indexOf("임하면 산불 진화 중입니다"));
    }

    // 결정: LLM 이 터지거나 약속한 단어가 아닌 답을 줘도 예외 없이 empty(판사 판정 불가)로 돌려준다.
    // 호출 경로는 call(Prompt) 라 스텁도 그걸 걸고 verify 로 실제 호출까지 확인한다(안 그러면 뼈대도 통과).
    // Red 단계: 뼈대가 호출하지 않아 verify 에서 실패한다.
    @Test
    @DisplayName("ChatModel 이 예외를 던지거나 판정 불가 답을 주면 예외 없이 empty")
    void returnsEmptyOnFailureOrUnreadableAnswer() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM down"));
        assertThat(judge.judgePair(earlier, later)).isEmpty();
        verify(chatModel).call(any(Prompt.class));

        // 약속한 단어가 아닌 답 -> 추측하지 않고 empty (별도 mock 으로 위 예외 스텁과 분리)
        ChatModel other = mock(ChatModel.class);
        when(other.call(any(Prompt.class))).thenReturn(answer("잘 모르겠습니다"));
        assertThat(new ClusteringJudge(other).judgeKind(earlier)).isEmpty();
        verify(other).call(any(Prompt.class));
    }

    // 결정: 분류 판정은 ADVISORY 답을 Optional.of(ADVISORY) 로 돌려준다.
    // Red 단계: 뼈대가 항상 empty 라서 "Expecting Optional to contain ADVISORY but was empty" 로 실패한다.
    @Test
    @DisplayName("judgeKind 는 ChatModel 이 ADVISORY 를 주면 Optional.of(ADVISORY)")
    void judgeKindReturnsAdvisory() {
        when(chatModel.call(any(Prompt.class))).thenReturn(answer("ADVISORY"));

        Optional<KindVerdict> verdict = judge.judgeKind(earlier);

        assertThat(verdict).contains(KindVerdict.ADVISORY);
    }
}
