package com.disaster.alert.alertapi.domain.disasteralert.repository;

import com.disaster.alert.alertapi.domain.disasteralert.dto.AlertSearchRequest;
import com.disaster.alert.alertapi.domain.disasteralert.dto.AlertWeatherDto;
import com.disaster.alert.alertapi.domain.disasteralert.dto.CombinedAlertResponse;
import com.disaster.alert.alertapi.domain.disasteralert.dto.DisasterAlertStatResponse;
import com.disaster.alert.alertapi.domain.disasteralert.dto.WeatherCorrelationDto;
import com.disaster.alert.alertapi.domain.disasteralert.dto.WeatherTypeStatDto;
import com.disaster.alert.alertapi.domain.disasteralert.dto.WeatherRegionStatDto;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface DisasterAlertRepositoryCustom {
    Page<DisasterAlert> searchAlerts(AlertSearchRequest alertSearchRequest, Pageable pageable);

    long countAlerts(AlertSearchRequest request);

    List<DisasterAlertStatResponse.RegionStat> countByRegion(AlertSearchRequest request);

    List<DisasterAlertStatResponse.TypeStat> countByType(AlertSearchRequest request);

    List<DisasterAlertStatResponse.LevelStat> countByLevel(AlertSearchRequest request);

    List<DisasterAlertStatResponse.RegionStat> getStatsSido(AlertSearchRequest request);

    List<DisasterAlertStatResponse.RegionStat> getStatsSigungu(AlertSearchRequest request);

    List<DisasterAlertStatResponse.DailyStat> getStatsByDate(AlertSearchRequest request);

    List<DisasterAlertStatResponse.HourlyStat> getStatsByHour(AlertSearchRequest request);

    List<DisasterAlertStatResponse.MonthlyTypeStat> getStatsByMonthType(AlertSearchRequest request);

    Page<CombinedAlertResponse> searchCombined(AlertSearchRequest request, String source, Pageable pageable);

    List<WeatherCorrelationDto> getWeatherCorrelation(AlertSearchRequest request);

    List<WeatherTypeStatDto> getWeatherByType(AlertSearchRequest request);

    List<WeatherRegionStatDto> getWeatherBySido(AlertSearchRequest request);

    List<WeatherRegionStatDto> getWeatherBySigungu(AlertSearchRequest request);

    // 시간별 (weather_observation 사용, 기간 7일 이하 전용)
    List<WeatherCorrelationDto> getWeatherHourlyCorrelation(AlertSearchRequest request);

    List<WeatherTypeStatDto> getWeatherHourlyByType(AlertSearchRequest request);

    List<WeatherRegionStatDto> getWeatherHourlyBySido(AlertSearchRequest request);

    List<WeatherRegionStatDto> getWeatherHourlyBySigungu(AlertSearchRequest request);

    Optional<AlertWeatherDto> getAlertWeather(Long alertId);
}
