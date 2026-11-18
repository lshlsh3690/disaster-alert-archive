package com.disaster.alert.alertapi.domain.event.evaluation;

import java.time.LocalDate;

/**
 * 클러스터링 "같은 사건" 판정 기준 문서의 버전 날짜(순수 상수 클래스, JPA·Spring 없음).
 *
 * <p>{@code specs/001-event-clustering-pipeline/CLUSTERING_CRITERIA.md} 를 바꾸면 이 값도 바꾼다 —
 * 불일치 쌍 사람 라벨은 이 날짜를 기준 시점으로 함께 저장하므로, 기준이 바뀐 뒤에 매긴 라벨과 이전 라벨을 구분할 수 있다.
 */
public final class ClusteringCriteria {

    /**
     * 2026-11-13 — 기준 문서의 경계 사례를 마지막으로 바꾼 날. 문서를 다시 바꾸면 이 날짜를 그날로 올린다.
     */
    public static final LocalDate VERSION_DATE = LocalDate.of(2026, 11, 13);

    private ClusteringCriteria() {
    }
}
