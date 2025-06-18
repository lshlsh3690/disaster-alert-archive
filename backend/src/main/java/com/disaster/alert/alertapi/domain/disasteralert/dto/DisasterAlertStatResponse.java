package com.disaster.alert.alertapi.domain.disasteralert.dto;

import lombok.Data;

import java.util.List;

@Data
public class DisasterAlertStatResponse {
    private int totalCount;
    private List<DisasterAlertRegionStatDto> regionStats;
}
