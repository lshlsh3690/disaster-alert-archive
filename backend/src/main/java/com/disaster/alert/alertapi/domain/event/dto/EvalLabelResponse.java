package com.disaster.alert.alertapi.domain.event.dto;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 평가 셋 라벨 수정 응답: 수정 뒤 그 라벨의 현재 값. 같은 라벨로 저장해 바뀐 게 없으면 기존 값 그대로다.
 */
public record EvalLabelResponse(long id, Label label, LocalDate criteriaDate, LocalDateTime updatedAt) {
}
