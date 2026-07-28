package com.disaster.alert.alertapi.global.logtrace;

import lombok.Getter;

// begin()과 end()/exception() 사이를 이어주는 토큰 — 시작 시각과 메시지를 들고 있다가
// 종료 시점에 소요 시간을 계산하는 데 쓰인다.
@Getter
public class TraceStatus {

    private final TraceId traceId;
    private final long startTimeMs;
    private final String message;

    public TraceStatus(TraceId traceId, long startTimeMs, String message) {
        this.traceId = traceId;
        this.startTimeMs = startTimeMs;
        this.message = message;
    }
}
