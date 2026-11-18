package com.disaster.alert.alertapi.domain.disasteralert.dto;

import java.time.LocalDateTime;

/**
 * 알림 한 건의 요약(id, 원문, 발송 시각, 유형). 다른 맥락(관리자 클러스터링 대시보드)이 알림 엔티티를 직접 만지지 않고
 * 원문만 읽을 수 있게 하는 읽기 전용 값이다. 발송 시각은 {@code created_at}(CRT_DT)이다.
 */
public record AlertSummaryDto(Long id, String message, LocalDateTime sentAt, String type) {
}
