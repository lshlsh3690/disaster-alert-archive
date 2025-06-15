package com.disaster.alert.alertapi.domain.disasteralert.dto;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class DisasterAlertResponseDto {
    private Long id;
    private Long sn;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedDate;
    private String disasterType;
    private DisasterLevel emergencyLevel;
    private List<String> regionNames;


    public static DisasterAlertResponseDto from(DisasterAlert disasterAlert) {
        return DisasterAlertResponseDto.builder()
                .id(disasterAlert.getId())
                .sn(disasterAlert.getSn())
                .message(disasterAlert.getMessage())
                .emergencyLevel(disasterAlert.getEmergencyLevel())
                .disasterType(disasterAlert.getDisasterType())
                .createdAt(disasterAlert.getCreatedAt())
//                .regionNames(disasterAlert.getRegions().stream()
//                        .map(region -> region.getLegalDistrict().getName())
//                        .collect(Collectors.toList()))
                .build();
    }
}