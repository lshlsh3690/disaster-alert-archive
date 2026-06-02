package com.disaster.alert.alertapi.domain.risk.service;

import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import com.disaster.alert.alertapi.domain.risk.model.DisasterRiskProfile;
import com.disaster.alert.alertapi.domain.risk.repository.DisasterRiskProfileRepository;
import com.disaster.alert.alertapi.domain.risk.service.dto.LlmProfileResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

/**
 * 35종에 없는 신규/특이 유형(예: 동물탈출, 화학사고 변종)의 위험 프로파일을 LLM 으로 산출.
 *
 * <p>이벤트 단위로만 호출(메시지마다 X) + DB 캐시(생성 후 disaster_risk_profile 저장)로 비용 통제.
 *
 * <p>I1 검증 반영:
 *  - responseFormat 수동 설정 안 함 — .entity() 의 BeanOutputConverter 가 포맷 관리.
 *  - LLM 거부/비JSON 응답 시 RuntimeException 던져지므로 try/catch 로 "기타" default fallback.
 *  - LLM 헛값(base_weight=5 등) 방어용 clamp.
 */
@Service
@Slf4j
public class LlmRiskProfiler {

    // Spring AI 1.0 은 ChatClient 빈을 자동 생성하지 않고 ChatClient.Builder 만 제공 →
    // 빌더를 주입받아 생성자에서 build() 한다.
    private final ChatClient chatClient;
    private final DisasterRiskProfileRepository profileRepo;

    public LlmRiskProfiler(ChatClient.Builder chatClientBuilder,
                           DisasterRiskProfileRepository profileRepo) {
        this.chatClient = chatClientBuilder.build();
        this.profileRepo = profileRepo;
    }

    @Transactional
    public DisasterRiskProfile resolveForEvent(DisasterEvent event) {
        String type = event.getPrimaryDisasterType();

        // 이미 생성/seed 되어 있으면 그대로 (캐시)
        var cached = profileRepo.findById(type);
        if (cached.isPresent()) return cached.get();

        if ("UNKNOWN".equals(type)) {
            // UNKNOWN 유형은 의미 없는 LLM 호출 낭비(및 API 요금 발생)를 막기 위해
            // 즉시 '기타' 프로파일로 폴백(fallback) 처리한다.
            log.debug("UNKNOWN 타입은 LLM을 호출하지 않고 '기타' 프로파일로 폴백합니다.");
            return profileRepo.findById("기타")
                    .orElseThrow(() -> new IllegalStateException("seed '기타' 프로파일 누락"));
        }

        try {
            LlmProfileResponse res = chatClient.prompt()
                    .options(OpenAiChatOptions.builder().model("gpt-4o-mini").build())
                    .user(buildPrompt(type, event.getEventTitle()))
                    .call()
                    .entity(LlmProfileResponse.class);

            double weight = clamp(res.baseWeight(), 0.0, 1.5);
            int halfLife = Math.max(1, res.halfLifeHours());

            DisasterRiskProfile profile = DisasterRiskProfile.ofLlm(type, weight, halfLife);
            profileRepo.save(profile);  // 다음부터 캐시 hit
            log.info("LLM 위험 프로파일 생성 type={} weight={} halfLife={} hint={}",
                    type, weight, halfLife, res.categoryHint());
            return profile;

        } catch (Exception ex) {
            log.warn("LLM 위험 프로파일 산출 실패 type={} — '기타' default 사용", type, ex);
            return profileRepo.findById("기타")
                    .orElseThrow(() -> new IllegalStateException("seed '기타' 프로파일 누락"));
        }
    }

    private String buildPrompt(String type, String sampleTitle) {
        return """
                너는 한국 재난안전 위험도 평가 보조 도구야.
                재난문자 유형 '%s' 의 위험 프로파일을 산출해줘.
                참고 샘플 제목: %s

                - base_weight: 인명·재산 위협 정도 (0.0 ~ 1.5, 지진/원전=1.0+, 단순 안내=0.3)
                - half_life_hours: 발생 후 위험이 절반으로 줄기까지 시간 (단발 사고=수시간, 폭염/감염병=수일~수주)
                - category_hint: 추정 카테고리 한 단어 (예: 동물탈출, 화학사고)

                JSON 객체로만 응답.
                """.formatted(type, sampleTitle == null ? "(제목 없음)" : sampleTitle);
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
