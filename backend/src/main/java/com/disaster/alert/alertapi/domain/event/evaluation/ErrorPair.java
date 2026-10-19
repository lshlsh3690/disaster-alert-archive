package com.disaster.alert.alertapi.domain.event.evaluation;

/** 놓친 쌍 또는 잘못 묶은 쌍 하나와 그 사실 정보. */
public record ErrorPair(long alertAId, long alertBId, PairFacts facts) {
}
