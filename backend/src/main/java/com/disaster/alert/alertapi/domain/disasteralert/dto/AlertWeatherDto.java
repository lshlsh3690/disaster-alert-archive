package com.disaster.alert.alertapi.domain.disasteralert.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AlertWeatherDto {
    private Double temp;
    private Double precip;
    private Double windSpeed;
}
