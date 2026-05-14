package com.disaster.alert.alertapi.domain.disasteralert.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 공식 재난문자 + 사용자 제보 통합 검색용 응답 DTO.
 *
 * <p>다국어 응답 시 OFFICIAL 항목만 번역 가능 (USER 항목은 사용자 제보라 번역 시스템 없음).
 * 따라서 USER 항목의 translated* 필드는 항상 null.
 */
@Getter
@Setter
@Builder
public class CombinedAlertResponse {
    public enum Source { OFFICIAL, USER }

    private Long id;
    private String message;
    private LocalDateTime createdAt;
    private String disasterType;
    private String emergencyLevelText;
    private List<String> regionNames;
    private Source source;

    // ─── 다국어 응답용 필드 (OFFICIAL 만 채워짐) ──────────────────────────────
    private String translatedMessage;
    private String translatedDisasterType;
    private List<String> translatedRegionNames;
    private String language;

    public static CombinedAlertResponse fromOfficial(DisasterAlertResponseDto o) {
        return CombinedAlertResponse.builder()
                .id(o.getId())
                .message(o.getMessage())
                .createdAt(o.getCreatedAt())
                .disasterType(o.getDisasterType())
                .emergencyLevelText(o.getEmergencyLevelText())
                .regionNames(o.getRegionNames())
                .source(Source.OFFICIAL)
                .build();
    }

    public static CombinedAlertResponse fromUser(UserAlertDtos.Response u) {
        return CombinedAlertResponse.builder()
                .id(u.getId())
                .message(u.getMessage())
                .createdAt(u.getCreatedAt())
                .disasterType(u.getDisasterType())
                .emergencyLevelText(u.getEmergencyLevelText())
                .regionNames(u.getRegionNames())
                .source(Source.USER)
                .build();
    }
}
