package com.disaster.alert.alertapi.domain.disasteralert.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 최신 재난문자 목록용 (대시보드/홈 화면).
 *
 * <p>topRegion 은 대표 지역 1개의 이름. 다국어 응답 시 {@code translatedTopRegion} 으로 대체된다.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
public class LatestAlertResponse {
    private Long id;
    private String message;
    private String disasterType;
    private LocalDateTime createdAt;
    private String topRegion;

    // ─── 다국어 응답용 필드 ──────────────────────────────
    private String translatedMessage;
    private String translatedDisasterType;
    private String translatedTopRegion;
    private String language;

    /**
     * JPQL 쿼리에서 사용하는 생성자.
     * 번역 필드 없이 원본만 채우고, 다국어 처리는 서비스 레이어에서 후처리.
     */
    public LatestAlertResponse(Long id, String message, String disasterType, LocalDateTime createdAt, String topRegion) {
        this.id = id;
        this.message = message;
        this.disasterType = disasterType;
        this.createdAt = createdAt;
        this.topRegion = topRegion;
    }
}
