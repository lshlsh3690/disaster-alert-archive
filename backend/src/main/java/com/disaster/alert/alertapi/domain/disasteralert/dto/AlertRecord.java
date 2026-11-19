package com.disaster.alert.alertapi.domain.disasteralert.dto;

import java.time.LocalDateTime;

/**
 * 원문 기록(기록관 스토리 1)에 쓰는 알림 한 건 — disasteralert 맥락 → archive 맥락 전달용.
 *
 * @param emergencyLevel 긴급 단계 표시 문구("안전안내" 등). 원 출처 값이 없으면 null
 * @param originalRegion 수신 지역명. 수집 때 "○○ 전체"의 "전체"와 쉼표 앞뒤 공백만 정리된 값(cleanRegionString)
 * @param modifiedDate   원 출처 정정 일자. 정정된 적 없으면 null
 * @param eventId        지금 속한 사건 ID. 묶이지 않았으면 null — 사건 링크 여부 판단에만 쓰고 화면에 노출하지 않는다
 * @param agencyLabel    본문의 발신 기관 표기 그대로(인용문에 쓴다). 표기가 없으면 null
 * @param agencyName     표기가 가리키는 기관 이름. 기관이 지정되지 않았으면 null
 */
public record AlertRecord(
        Long alertId,
        Long sn,
        String message,
        LocalDateTime createdAt,
        LocalDateTime modifiedDate,
        String disasterType,
        String emergencyLevel,
        String originalRegion,
        Long eventId,
        String agencyLabel,
        String agencyName
) {
}
