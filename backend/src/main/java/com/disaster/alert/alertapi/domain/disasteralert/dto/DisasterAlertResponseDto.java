package com.disaster.alert.alertapi.domain.disasteralert.dto;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 재난문자 검색 목록용 응답 DTO.
 *
 * <p>다국어 응답을 위해 번역 필드들이 추가되어 있다.
 * {@code lang=ko}(기본) 요청이면 번역 필드는 모두 null,
 * {@code lang=en|ja|zh} 요청이면 번역 결과로 채워진다.
 */
@Getter
@Setter
@Builder
public class DisasterAlertResponseDto {
    private Long id;
    private Long sn;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedDate;
    private String disasterType;
    private String emergencyLevelText;
    private List<String> regionNames;

    // ─── 다국어 응답용 필드 ──────────────────────────────
    /** 번역된 본문 (lang 미지정 시 null) */
    private String translatedMessage;
    /** 번역된 재난 유형 (lang 미지정 시 null) */
    private String translatedDisasterType;
    /** 번역된 지역명 리스트 (lang 미지정 시 null) */
    private List<String> translatedRegionNames;
    /** 응답 언어 코드 (예: "en", "ja", "zh"). lang 미지정 시 null */
    private String language;


    public static DisasterAlertResponseDto from(DisasterAlert disasterAlert) {
        return DisasterAlertResponseDto.builder()
                .id(disasterAlert.getId())
                .sn(disasterAlert.getSn())
                .message(disasterAlert.getMessage())
                .emergencyLevelText(
                        disasterAlert.getEmergencyLevel() != null ? disasterAlert.getEmergencyLevel().getDescription() : null
                )
                .disasterType(disasterAlert.getDisasterType())
                .createdAt(disasterAlert.getCreatedAt())
                .modifiedDate(disasterAlert.getModifiedDate())
                .regionNames(Optional.ofNullable(disasterAlert.getDisasterAlertRegions()).orElse(List.of())
                        .stream()
                        .map(region -> region.getLegalDistrict().getName())
                        .collect(Collectors.toList()))
                .build();
    }
}
