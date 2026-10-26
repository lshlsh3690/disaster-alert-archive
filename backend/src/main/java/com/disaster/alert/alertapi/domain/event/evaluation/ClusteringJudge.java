package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 클러스터링 성능 평가의 정답을 매기는 판사(LLM-as-a-Judge). 설계안 ③ 참조
 * (docs/event-clustering/ADMIN_EVAL_DASHBOARD_DESIGN.md).
 * 평가 회차와 판사 신뢰도 측정 도구가 부른다. 실패·해석 불가는 예외 대신 empty(=판사 판정 불가)로 돌려줘
 * 호출자가 추측 없이 따로 센다.
 * 공유 ChatModel 의 기본 모델(gpt-4o-mini)이 아니라 호출마다 {@link #MODEL} 로 지정한다 —
 * 클러스터링 LLM 폴백과 같은 모델이면 같은 실수를 같이 해서 판사가 오류를 못 잡는다.
 * 시스템 프롬프트(judge/ 리소스)는 CLUSTERING_CRITERIA.md 1·2절을 옮긴 것이고 기동 시 1회 읽는다.
 * 예시(few-shot)는 넣지 않는다 — 사람 라벨에서 뽑으면 그 라벨로 재는 신뢰도가 부풀기 때문이다.
 */
@Slf4j
@Service
public class ClusteringJudge {

    static final String MODEL = "gpt-4o";
    static final String PAIR_PROMPT_RESOURCE = "judge/pair-system-prompt.txt";
    static final String KIND_PROMPT_RESOURCE = "judge/kind-system-prompt.txt";

    private static final DateTimeFormatter SENT_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ChatModel chatModel;
    /** 로드 실패 시 null — 해당 판정은 항상 empty 다. */
    private final String pairPrompt;
    private final String kindPrompt;

    public ClusteringJudge(ChatModel chatModel) {
        this.chatModel = chatModel;
        this.pairPrompt = loadPrompt(PAIR_PROMPT_RESOURCE);
        this.kindPrompt = loadPrompt(KIND_PROMPT_RESOURCE);
    }

    /**
     * @param earlier 먼저 발송된 알림(입력에서 "알림 1")
     * @param later   나중에 발송된 알림(입력에서 "알림 2")
     * @return 두 알림이 같은 사건인지. 본문이 비었거나 LLM 실패·해석 불가면 empty.
     */
    public Optional<PairVerdict> judgePair(DisasterAlert earlier, DisasterAlert later) {
        // 1) 본문이 하나라도 없으면 판단 근거가 없다 — 호출하지 않는다(비용, 추측 방지)
        if (isBlank(earlier.getMessage()) || isBlank(later.getMessage())) {
            return Optional.empty();
        }
        String user = alertBlock("알림 1", earlier) + "\n\n" + alertBlock("알림 2", later);
        return ask(pairPrompt, user).map(PairVerdict::fromModelAnswer);
    }

    /** @return 알림이 사건인지 비사건인지. 본문이 비었거나 LLM 실패·해석 불가면 empty. */
    public Optional<KindVerdict> judgeKind(DisasterAlert alert) {
        if (isBlank(alert.getMessage())) {
            return Optional.empty();
        }
        // 분류기(AlertKindClassifier)와 같은 입력 모양: [유형:X] 공백 정리한 원문
        String user = "[유형:" + alert.getDisasterType() + "] " + normalize(alert.getMessage());
        return ask(kindPrompt, user).map(KindVerdict::fromModelAnswer);
    }

    /** 시스템 프롬프트 + 입력으로 gpt-4o·온도 0 호출. 프롬프트가 없거나 호출이 실패하면 empty. */
    private Optional<String> ask(String systemPrompt, String user) {
        if (systemPrompt == null) {
            return Optional.empty();
        }
        try {
            // 호출마다 모델·온도를 지정한다 — 안 하면 공유 설정(gpt-4o-mini)으로 가서 폴백과 같은 모델이 된다
            Prompt prompt = new Prompt(
                    List.of(new SystemMessage(systemPrompt), new UserMessage(user)),
                    OpenAiChatOptions.builder().model(MODEL).temperature(0.0).build());
            return Optional.ofNullable(chatModel.call(prompt).getResult().getOutput().getText());
        } catch (Exception e) {
            // 판정 불가는 정상 흐름이라 사람이 조치할 일이 아니다 — error 가 아닌 warn
            log.warn("판사 호출 실패, 판정 불가로 둡니다: {}", e.toString());
            return Optional.empty();
        }
    }

    /** 한 알림을 유형·수신 지역·발송 시각·원문 네 줄로 만든다. 지역이 없으면 "(없음)". */
    private static String alertBlock(String heading, DisasterAlert a) {
        String region = isBlank(a.getOriginalRegion()) ? "(없음)" : a.getOriginalRegion();
        String sentAt = a.getCreatedAt() == null ? "(없음)" : a.getCreatedAt().format(SENT_AT);
        return heading + "\n"
                + "유형: " + a.getDisasterType() + "\n"
                + "수신 지역: " + region + "\n"
                + "발송 시각: " + sentAt + "\n"
                + "원문: " + normalize(a.getMessage());
    }

    private static String normalize(String text) {
        return text.replaceAll("\s+", " ").trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String loadPrompt(String resource) {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 리소스가 깨졌다고 앱 기동을 막지 않는다 — 판사만 포기한다(AlertKindClassifier 와 같은 방침)
            log.warn("판사 프롬프트 리소스 로드 실패({}), 해당 판정은 항상 판정 불가입니다: {}", resource, e.toString());
            return null;
        }
    }
}
