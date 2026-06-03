package com.disaster.alert.alertapi.domain.disasteralert.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class WeatherRegionStatDto {
    private String date;
    private String region;
    private long count;
    private Double avgTemp;
    private Double minTemp;
    private Double maxTemp;
    private Double maxPrecip;
}
