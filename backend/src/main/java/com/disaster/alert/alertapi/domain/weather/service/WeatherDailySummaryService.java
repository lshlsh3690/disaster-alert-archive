package com.disaster.alert.alertapi.domain.weather.service;

import com.disaster.alert.alertapi.domain.weather.repository.WeatherDailySummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeatherDailySummaryService {

    private final WeatherDailySummaryRepository summaryRepository;

    /**
     * 특정 날짜의 weather_observation 을 집계해 weather_daily_summary 에 upsert.
     *
     * @return 집계된 시군구 row 수
     */
    @Transactional
    public int aggregate(LocalDate targetDate) {
        int rows = summaryRepository.aggregateByDate(targetDate);
        log.info("weather_daily_summary 집계 완료: date={}, rows={}", targetDate, rows);
        return rows;
    }
}
