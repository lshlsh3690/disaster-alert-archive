package com.disaster.alert.alertapi.domain.disasteralert.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DisasterAlertRegionStatDto {
    private String region;
    private int count;
}
