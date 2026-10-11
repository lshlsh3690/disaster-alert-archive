package com.disaster.alert.alertapi.domain.event.evaluation;

/** 쌍 단위 혼동 행렬과 파생 지표. 분모가 0 이면 지표는 0.0. */
public record Confusion(int tp, int fp, int fn, int tn, double precision, double recall, double f1) {
}
