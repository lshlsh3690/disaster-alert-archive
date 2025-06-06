package com.disaster.alert.alertapi.domain.disasteralert.dto;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

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
}