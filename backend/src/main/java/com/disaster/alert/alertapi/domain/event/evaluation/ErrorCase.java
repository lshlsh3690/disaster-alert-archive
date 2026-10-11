package com.disaster.alert.alertapi.domain.event.evaluation;

/** 틀린 쌍 하나와 그 원인. */
public record ErrorCase(long alertAId, long alertBId, EvaluationStratum stratum, ErrorCause cause) {
}
