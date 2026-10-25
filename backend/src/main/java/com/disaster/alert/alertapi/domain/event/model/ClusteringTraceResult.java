package com.disaster.alert.alertapi.domain.event.model;

/** 알림 1건의 클러스터링 결과 (clustering_trace.result). */
public enum ClusteringTraceResult {
    /** 기존 이벤트에 병합. */
    MERGED,
    /** 새 이벤트 생성. */
    NEW,
    /** 클러스터링 제외(비사건·본문 없음). */
    SKIPPED
}
