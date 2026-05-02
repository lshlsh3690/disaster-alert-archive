package com.disaster.alert.alertapi.domain.disasteralert.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
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


