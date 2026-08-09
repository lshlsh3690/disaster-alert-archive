package com.disaster.alert.alertapi.global.translation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 번역 기능 설정.
 *
 * <p>번역 엔진은 {@link OpenAiTranslationClient}(OpenAI) 하나뿐이라 API 키·URL 설정이 없다.
 * OpenAI 자격증명은 {@code spring.ai.openai.api-key} 를 임베딩·LLM 판정과 공유한다.
 * (DeepL 시절에는 여기에 api-key/api-key2/api-url 이 있었다 — 평생 누적 쿼터 소진으로 제거)
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "translation")
public class TranslationProperties {
    /** false 면 번역을 아예 시도하지 않고 원문을 그대로 노출한다. */
    private boolean enabled;
}
