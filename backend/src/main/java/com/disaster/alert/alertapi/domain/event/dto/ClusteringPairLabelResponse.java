package com.disaster.alert.alertapi.domain.event.dto;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 불일치 쌍 라벨 저장 응답(계약 1절). alertA &lt; alertB 로 정렬된 id 와 저장된 라벨·기준 시점·저장 시각.
 *
 * <p>Red 단계 뼈대 - 값을 담는 구조만 있다.
 */
public record ClusteringPairLabelResponse(long alertA, long alertB, Label label, LocalDate criteriaDate,
                                          LocalDateTime updatedAt) {
}
