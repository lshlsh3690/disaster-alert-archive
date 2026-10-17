package com.disaster.alert.alertapi.domain.disasteralert.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertKindClassifierTest {

    // 결정: LLM 호출이 터져도 예외를 밖으로 내보내지 않고 empty 를 돌려준다(분류 실패가 수집을 막으면 안 됨).
    // 주의: 구현이 부르는 건 call(Prompt) 라서 스텁도 그걸 걸어야 한다. call(String) 에 걸면 NPE 가 catch 에
    // 잡혀 예외 처리와 무관하게 통과한다. verify 로 실제 호출 경로까지 확인한다.
    @Test
    @DisplayName("ChatModel 호출이 예외를 던지면 예외 없이 Optional.empty 를 돌려준다")
    void returnsEmptyWhenChatModelThrows() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM down"));

        assertThat(new AlertKindClassifier(chatModel).classify("산불", "OO읍 산24 산불 발생")).isEmpty();
        verify(chatModel).call(any(Prompt.class));
    }
}
