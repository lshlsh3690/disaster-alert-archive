package com.disaster.alert.alertapi.global.logtrace;

import lombok.Getter;

// begin()과 end()/exception() 사이를 이어주는 토큰 — 시작 시각과 메시지를 들고 있다가
// 종료 시점에 소요 시간을 계산하는 데 쓰인다.
// startTimeNanos는 System.nanoTime() 기준 — currentTimeMillis()는 NTP 보정 등으로 시스템 시계가
// 튈 수 있어 소요시간이 음수/이상값으로 찍힐 수 있는데, nanoTime()은 단조 증가라 그럴 일이 없다.
@Getter
public class TraceStatus {

    private final TraceId traceId;
    private final long startTimeNanos;
    private final String message;

    public TraceStatus(TraceId traceId, long startTimeNanos, String message) {
        this.traceId = traceId;
        this.startTimeNanos = startTimeNanos;
        this.message = message;
    }
}
