package com.disaster.alert.alertapi.domain.risk.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * LLM 위험 프로파일 산출 응답 매핑용 record.
 * Spring AI .entity(LlmProfileResponse.class) 가 BeanOutputConverter 로 채워준다.
 */
public record LlmProfileResponse(
        @JsonProperty("base_weight") double baseWeight,
        @JsonProperty("half_life_hours") int halfLifeHours,
        @JsonProperty("category_hint") String categoryHint
) {}
