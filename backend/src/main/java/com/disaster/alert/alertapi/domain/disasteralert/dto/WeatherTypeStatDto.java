package com.disaster.alert.alertapi.domain.disasteralert.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class WeatherTypeStatDto {
    private String date;
    private String type;
    private long count;
    private Double avgTemp;
    private Double minTemp;
    private Double maxTemp;
    private Double maxPrecip;
}
