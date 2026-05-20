package com.disaster.alert.alertapi.domain.openapi.dto;

import com.disaster.alert.alertapi.domain.disasteralert.dto.DisasterAlertResponseDto;
import com.disaster.alert.alertapi.domain.openapi.support.OpenApiCsvRow;

import java.util.List;
import java.util.stream.Collectors;

/**
 * OpenAPI 재난문자 CSV 응답의 한 행.
 *
 * <p>CSV는 OpenAPI 표현 방식이므로 검색 서비스와 분리해 응답 DTO가 컬럼 구조를 담당한다.
 * 공통 OpenApiCsvWriter는 이 row가 제공하는 headers와 values만 이용해 CSV를 생성한다.
 */
public record OpenApiAlertCsvRow(
        /** 재난문자 내부 식별자. */
        String id,
        /** 공공데이터 원본 일련번호. */
        String sn,
        /** 재난문자 생성 시각. */
        String createdAt,
        /** 재난문자 수정 시각. */
        String modifiedDate,
        /** 재난 유형. */
        String disasterType,
        /** 긴급 단계 설명. */
        String emergencyLevel,
        /** 관련 지역명 목록. */
        String regions,
        /** 원문 메시지. */
        String message,
        /** 번역 응답 언어. */
        String language,
        /** 번역된 재난 유형. */
        String translatedDisasterType,
        /** 번역된 지역명 목록. */
        String translatedRegions,
        /** 번역된 메시지. */
        String translatedMessage
) implements OpenApiCsvRow {
    private static final List<String> HEADERS = List.of(
            "id",
            "sn",
            "createdAt",
            "modifiedDate",
            "disasterType",
            "emergencyLevel",
            "regions",
            "message",
            "language",
            "translatedDisasterType",
            "translatedRegions",
            "translatedMessage"
    );

    /** 재난문자 JSON 응답 DTO를 CSV row DTO로 변환한다. */
    public static OpenApiAlertCsvRow from(DisasterAlertResponseDto dto) {
        return new OpenApiAlertCsvRow(
                stringify(dto.getId()),
                stringify(dto.getSn()),
                stringify(dto.getCreatedAt()),
                stringify(dto.getModifiedDate()),
                dto.getDisasterType(),
                dto.getEmergencyLevelText(),
                join(dto.getRegionNames()),
                dto.getMessage(),
                dto.getLanguage(),
                dto.getTranslatedDisasterType(),
                join(dto.getTranslatedRegionNames()),
                dto.getTranslatedMessage()
        );
    }

    /** CSV 첫 줄에 사용할 컬럼 목록을 반환한다. */
    @Override
    public List<String> headers() {
        return HEADERS;
    }

    /** CSV 한 줄에 기록할 값 목록을 컬럼 순서대로 반환한다. */
    @Override
    public List<String> values() {
        return List.of(
                nullToEmpty(id),
                nullToEmpty(sn),
                nullToEmpty(createdAt),
                nullToEmpty(modifiedDate),
                nullToEmpty(disasterType),
                nullToEmpty(emergencyLevel),
                nullToEmpty(regions),
                nullToEmpty(message),
                nullToEmpty(language),
                nullToEmpty(translatedDisasterType),
                nullToEmpty(translatedRegions),
                nullToEmpty(translatedMessage)
        );
    }

    /** null 값을 빈 문자열로 변환해 CSV writer가 단순하게 동작하도록 한다. */
    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 객체 값을 문자열로 변환한다. null이면 빈 값을 유지하기 위해 null을 반환한다. */
    private static String stringify(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** 여러 지역명을 CSV 한 칸에 담기 위해 파이프 문자로 결합한다. */
    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining("|"));
    }
}
