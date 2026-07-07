package com.disaster.alert.alertapi.domain.disasteralert.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class WeatherCorrelationDto {
    private String date;
    private long count;
    private Double avgTemp;
    private Double minTemp;
    private Double maxTemp;
    private Double maxPrecip;
    private Double avgWindSpeed;
    private String primaryType;
}
