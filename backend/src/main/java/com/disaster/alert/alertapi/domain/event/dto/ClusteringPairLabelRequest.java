package com.disaster.alert.alertapi.domain.event.dto;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import jakarta.validation.constraints.NotNull;

/**
 * 불일치 쌍 라벨 저장 요청(계약 1절). 두 알림 id 의 순서는 상관없다 — 서버가 작은 id 를 a 로 맞춘다.
 * 회원 id 와 기준 시점은 요청이 아니라 서버가 붙인다.
 */
public record ClusteringPairLabelRequest(@NotNull Long alertA, @NotNull Long alertB, @NotNull Label label) {
}
