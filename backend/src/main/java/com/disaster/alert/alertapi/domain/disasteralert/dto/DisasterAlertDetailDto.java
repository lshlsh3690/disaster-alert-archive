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
    private String emergencyLevelText;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedDate;
    private String originalRegion;
    private List<String> legalDistrictNames;
    private Long eventId;
    private String translatedMessage;
    private String translatedDisasterType;
    private List<String> translatedRegionNames;
    private String language;

    public DisasterAlertDetailDto(DisasterAlert alert, List<String> legalDistrictNames) {
        this.id = alert.getId();
        this.sn = alert.getSn();
        this.message = alert.getMessage();
        this.disasterType = alert.getDisasterType();
        this.emergencyLevelText = alert.getEmergencyLevel() != null ? alert.getEmergencyLevel().getDescription() : null;
        this.createdAt = alert.getCreatedAt();
        this.modifiedDate = alert.getModifiedDate();
        this.originalRegion = alert.getOriginalRegion();
        this.legalDistrictNames = legalDistrictNames;
    }
}

