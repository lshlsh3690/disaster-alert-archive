package com.disaster.alert.alertapi.domain.event.dto;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 평가 셋 라벨 하나의 변경 이력(최신 먼저). 정답이 언제·누가·왜 바뀌었는지 화면에서 확인하는 용도다.
 */
public record EvalLabelHistoryResponse(long labelId, List<Change> changes) {

    /** 변경 한 번. changedBy 는 바꾼 관리자의 회원 id. */
    public record Change(Label oldLabel, Label newLabel, String reason, long changedBy, LocalDateTime changedAt) {
    }
}
