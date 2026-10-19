package com.disaster.alert.alertapi.domain.event.service;

/**
 * 알림 단위 클러스터링 판정 기록을 받는 곳. 기본은 아무것도 안 하고(운영), backfill 프로필에서만 DB 에 남긴다.
 */
public interface ClusteringTraceRecorder {

    void record(ClusteringTrace trace);
}
