package com.disaster.alert.alertapi.domain.event.service;

import org.springframework.stereotype.Component;

/** 기본 구현 — 운영 수집 경로는 아무것도 쓰지 않는다. backfill 프로필에서는 JDBC 구현이 @Primary 로 대체한다. */
@Component
public class NoOpClusteringTraceRecorder implements ClusteringTraceRecorder {

    @Override
    public void record(ClusteringTrace trace) {
        // 의도적으로 비어 있음
    }
}
