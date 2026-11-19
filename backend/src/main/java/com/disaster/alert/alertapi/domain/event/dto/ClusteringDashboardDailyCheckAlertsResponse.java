package com.disaster.alert.alertapi.domain.event.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 대시보드 "구조 검사" 표에서 한 날짜 행을 펼쳤을 때 보이는 그날의 문제 알림 목록.
 *
 * <p>그날 사건 알림 전부가 아니라 구조 검사 숫자가 센 문제 알림(이벤트 미배정 또는 임베딩 없음)만 담는다 —
 * 숫자와 목록이 같은 기준이어야 "미배정 3"을 눌렀을 때 3건이 나온다. 발송 시각 오름차순이다.
 *
 * <p>Red 단계 뼈대 - 값을 담는 구조만 있다.
 */
public record ClusteringDashboardDailyCheckAlertsResponse(LocalDate date, List<Alert> alerts) {

    /**
     * 문제 알림 하나. unassigned = 이벤트 없음, noEmbedding = 임베딩 없음(둘 다 true 일 수 있다).
     * 이벤트에 배정돼 있으면 eventId·mergeMethod 가 있고, 미배정이면 null 이다.
     */
    public record Alert(long id, LocalDateTime sentAt, String type, String message,
                        boolean unassigned, boolean noEmbedding, Long eventId, String mergeMethod) {
    }
}
