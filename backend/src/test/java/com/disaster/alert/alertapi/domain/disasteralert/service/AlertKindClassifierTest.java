package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.AlertKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlertKindClassifierTest {

    /**
     * 결정: LLM 호출이 터져도 분류기는 예외를 밖으로 내보내지 않는다. 분류 실패가 수집을 막으면 안 된다.
     * Red: 뼈대가 ChatModel 을 부르지 않고 ADVISORY 를 돌려줘서 empty 기대와 달라 실패한다.
     */
    @Test
    @DisplayName("ChatModel 호출이 예외를 던지면 예외 없이 Optional.empty 를 돌려준다")
    void returnsEmptyWhenChatModelThrows() {
        // 준비: LLM 이 장애(타임아웃 등)를 내는 상황
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(anyString())).thenThrow(new RuntimeException("LLM timeout"));
        AlertKindClassifier classifier = new AlertKindClassifier(chatModel);

        // 실행: 예외가 전파되면 여기서 테스트가 깨진다
        Optional<AlertKind> result = classifier.classify("산불", "OO읍 산24 산불 발생");

        // 검증: 실패는 '분류 없음'으로 표현해 호출자가 폴백하게 한다
        assertThat(result).isEmpty();
    }
}
