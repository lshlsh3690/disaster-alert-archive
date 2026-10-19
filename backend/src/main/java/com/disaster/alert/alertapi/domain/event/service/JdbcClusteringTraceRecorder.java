package com.disaster.alert.alertapi.domain.event.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** backfill 프로필 전용 — clustering_trace 에 알림당 한 행을 INSERT 한다. */
// Red 단계 뼈대 — 구현은 Green 에서 (지금은 INSERT 하지 않는다)
@Component
@Profile("backfill")
@Primary
@RequiredArgsConstructor
public class JdbcClusteringTraceRecorder implements ClusteringTraceRecorder {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void record(ClusteringTrace trace) {
        // Green 에서 INSERT INTO clustering_trace ... 를 구현한다
    }
}
