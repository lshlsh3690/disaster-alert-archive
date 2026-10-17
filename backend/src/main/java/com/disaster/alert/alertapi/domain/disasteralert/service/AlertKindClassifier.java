package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.AlertKind;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 재난문자를 LLM 으로 사건/비사건 분류한다. 수집 루프가 새 알림마다 동기로 부르며,
 * 실패는 예외 대신 empty(=미분류)로 돌려줘 수집·클러스터링을 막지 않는다.
 * 공유 ChatModel(온도는 설정 기본값 0)을 그대로 쓰고, 프롬프트·예시는 alert-kind/ 리소스에서 기동 시 1회 읽는다.
 */
@Slf4j
@Service
public class AlertKindClassifier {

    private final ChatModel chatModel;
    /** 시스템 프롬프트 + few-shot 쌍. 로드 실패 시 null 이고 classify 는 항상 empty 다. */
    private final List<Message> basePrompt;

    public AlertKindClassifier(ChatModel chatModel) {
        this.chatModel = chatModel;
        List<Message> loaded = null;
        try {
            loaded = loadBasePrompt();
        } catch (Exception e) {
            // 리소스가 깨졌다고 앱 기동을 막지 않는다 — 분류만 포기하고 수집은 계속한다.
            log.warn("알림 분류 프롬프트 리소스 로드 실패, 분류를 건너뜁니다: {}", e.toString());
        }
        this.basePrompt = loaded;
    }

    /** @return 분류 결과. 본문이 비었거나 LLM 실패·해석 불가면 empty(호출자는 미분류로 둔다). */
    public Optional<AlertKind> classify(String disasterType, String message) {
        // 1) 본문이 없거나 프롬프트가 없으면 호출하지 않는다(비용)
        if (message == null || message.isBlank() || basePrompt == null) {
            return Optional.empty();
        }
        try {
            // 2) 프롬프트 + 예시 뒤에 이번 알림을 마지막 user 메시지로 붙여 호출한다
            List<Message> messages = new ArrayList<>(basePrompt);
            messages.add(new UserMessage(userText(disasterType, message)));
            String answer = chatModel.call(new Prompt(messages)).getResult().getOutput().getText();
            // 3) 정확히 읽히지 않는 답은 추측하지 않는다(null → empty)
            return Optional.ofNullable(AlertKind.fromModelAnswer(answer));
        } catch (Exception e) {
            // 정상 흐름의 분류 실패는 사람이 조치할 일이 아니라 error 가 아닌 warn
            log.warn("알림 분류 실패, 미분류로 둡니다: {}", e.toString());
            return Optional.empty();
        }
    }

    private List<Message> loadBasePrompt() throws Exception {
        List<Message> messages = new ArrayList<>();
        try (InputStream in = new ClassPathResource("alert-kind/system-prompt.txt").getInputStream()) {
            messages.add(new SystemMessage(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        }
        try (InputStream in = new ClassPathResource("alert-kind/shots.json").getInputStream()) {
            for (JsonNode shot : new ObjectMapper().readTree(in)) {
                messages.add(new UserMessage(userText(shot.get("type").asText(), shot.get("message").asText())));
                messages.add(new AssistantMessage(shot.get("answer").asText()));
            }
        }
        return List.copyOf(messages);
    }

    /** 예시와 실제 입력이 같은 형식·공백 정규화를 쓰도록 한 곳에서 만든다. */
    private static String userText(String type, String message) {
        return "[유형:" + type + "] " + message.replaceAll("\\s+", " ").trim();
    }
}
