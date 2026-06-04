package com.disaster.alert.alertapi.domain.weather.scheduler;

import com.disaster.alert.alertapi.domain.weather.service.WeatherDailySummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 일별 날씨 집계 스케줄러.
 *
 * <p>매일 00:05 KST 에 전날 데이터를 집계한다.
 * 00:00 정각 대신 00:05 로 잡은 이유:
 * WeatherCollectScheduler 가 매시 45분에 실행되므로 자정 직전(23:45)의 관측 데이터가
 * DB에 완전히 적재된 뒤 집계하기 위한 여유 시간.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WeatherDailySummaryScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final WeatherDailySummaryService summaryService;

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    public void runDaily() {
        LocalDate yesterday = LocalDate.now(KST).minusDays(1);
        log.info("WeatherDailySummaryScheduler 시작: targetDate={}", yesterday);
        try {
            int rows = summaryService.aggregate(yesterday);
            log.info("WeatherDailySummaryScheduler 완료: targetDate={}, rows={}", yesterday, rows);
        } catch (Exception e) {
            log.error("WeatherDailySummaryScheduler 실패: targetDate={}", yesterday, e);
        }
    }
}
