package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.AlertKind;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 재난문자를 LLM 으로 사건/비사건 분류한다. 실패는 예외 대신 empty 로 돌려준다.
 */
// Red 단계 뼈대 — 구현은 Green 에서
@Service
@RequiredArgsConstructor
public class AlertKindClassifier {

    private final ChatModel chatModel;

    public Optional<AlertKind> classify(String disasterType, String message) {
        return Optional.of(AlertKind.ADVISORY);
    }
}
