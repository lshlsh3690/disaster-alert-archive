package com.disaster.alert.alertapi.domain.disasteralert.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class LatestAlertResponse {
    private Long id;
    private String message;
    private String disasterType;
    private LocalDateTime createdAt;
    private String topRegion;
}
