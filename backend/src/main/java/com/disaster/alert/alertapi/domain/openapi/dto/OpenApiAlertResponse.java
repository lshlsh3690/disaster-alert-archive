package com.disaster.alert.alertapi.domain.openapi.dto;

import com.disaster.alert.alertapi.domain.disasteralert.dto.DisasterAlertResponseDto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OpenAPI 재난문자 조회 응답 DTO.
 *
 * <p>내부 DisasterAlertResponseDto와 분리해 외부 API 계약을 독립적으로 관리한다.
 * 내부 DTO가 변경되더라도 이 DTO를 유지하는 한 외부 API 응답 형태는 변하지 않는다.
 */
public record OpenApiAlertResponse(
        Long id,
        Long sn,
        String message,
        LocalDateTime createdAt,
        LocalDateTime modifiedDate,
        String disasterType,
        String emergencyLevel,
        List<String> regionNames,
        String language,
        String translatedMessage,
        String translatedDisasterType,
        List<String> translatedRegionNames
) {
    /** 내부 응답 DTO를 OpenAPI 응답 DTO로 변환한다. */
    public static OpenApiAlertResponse from(DisasterAlertResponseDto dto) {
        return new OpenApiAlertResponse(
                dto.getId(),
                dto.getSn(),
                dto.getMessage(),
                dto.getCreatedAt(),
                dto.getModifiedDate(),
                dto.getDisasterType(),
                dto.getEmergencyLevelText(),
                dto.getRegionNames(),
                dto.getLanguage(),
                dto.getTranslatedMessage(),
                dto.getTranslatedDisasterType(),
                dto.getTranslatedRegionNames()
        );
    }
}
