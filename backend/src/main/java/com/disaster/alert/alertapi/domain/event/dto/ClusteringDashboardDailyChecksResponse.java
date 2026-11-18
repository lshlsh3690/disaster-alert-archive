package com.disaster.alert.alertapi.domain.event.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 대시보드 "구조 검사" 응답(계약 3절). 요청 범위의 모든 날이 최신 날짜 먼저로 들어간다.
 *
 * <p>Red 단계 뼈대 - 값을 담는 구조만 있다.
 */
public record ClusteringDashboardDailyChecksResponse(LocalDate from, LocalDate to, List<Day> days) {

    /**
     * 날짜 하나. recorded=false 인 날은 날짜와 recorded 만 채우고 나머지는 null 이라 JSON 에서 빠진다.
     * attention = 미배정 > 0 또는 임베딩 없음 > 0.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Day(LocalDate date, boolean recorded,
                      Integer incidentCount, Integer unassignedCount, Integer noEmbeddingCount,
                      Boolean attention) {
    }
}
