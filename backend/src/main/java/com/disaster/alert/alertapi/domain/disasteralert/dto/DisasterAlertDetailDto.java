package com.disaster.alert.alertapi.domain.disasteralert.dto;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;


@Data
public class DisasterAlertDetailDto {
    private Long id;
    private Long sn;
    private String message;
    private String disasterType;
    private String emergencyLevel;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedDate;
    private String originalRegion;
    private List<String> legalDistrictNames;

    public DisasterAlertDetailDto(DisasterAlert alert, List<String> legalDistrictNames) {
        this.id = alert.getId();
        this.sn = alert.getSn();
        this.message = alert.getMessage();
        this.disasterType = alert.getDisasterType();
        this.emergencyLevel = alert.getEmergencyLevel() != null ? alert.getEmergencyLevel().name() : null;
        this.createdAt = alert.getCreatedAt();
        this.modifiedDate = alert.getModifiedDate();
        this.originalRegion = alert.getOriginalRegion();
        this.legalDistrictNames = legalDistrictNames;
    }
}

