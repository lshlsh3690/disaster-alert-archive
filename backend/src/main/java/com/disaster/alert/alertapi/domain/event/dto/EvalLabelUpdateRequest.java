package com.disaster.alert.alertapi.domain.event.dto;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 평가 셋 라벨 수정 요청. 사유는 필수 — 정답을 바꾸는 일이라 이유 없는 수정은 받지 않는다.
 * 회원 id 와 기준 시점은 요청이 아니라 서버가 붙인다.
 */
public record EvalLabelUpdateRequest(@NotNull Label label, @NotBlank String reason) {
}
