package com.disaster.alert.alertapi.domain.event.evaluation;

/** 오류 쌍의 원인 분류. */
public enum ErrorCause {
    OVER_MERGE, RETRIEVAL_MISS, DECISION_MISS, KEY_PATH_MISS, UNKNOWN_MISS
}
