package com.disaster.alert.alertapi.domain.weather.scheduler;

import com.disaster.alert.alertapi.domain.disasteralert.constant.StatsCacheNames;
import com.disaster.alert.alertapi.domain.weather.service.WeatherCollectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 초단기실황 수집 스케줄러.
 *
 * <p><b>cron 시간 결정 이유</b>
 * <ul>
 *   <li>기상청은 매시 정각 데이터를 매시 40분에 발표</li>
 *   <li>안전하게 매시 45분에 호출 (5분 여유)</li>
 *   <li>이 시각에 호출하면 직전 정각 (예: 14:00) 의 관측값을 받음</li>
 * </ul>
 *
 * <p><b>예외 처리</b>: 단일 사이클 전체 실패해도 다음 시각 정상 시도.
 * 격자별 부분 실패는 {@link WeatherCollectService} 내부에서 처리.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WeatherCollectScheduler {

    private final WeatherCollectService weatherCollectService;

    /**
     * 매시 45분 자동 수집 (한국 표준시 기준).
     * cron: 초 분 시 일 월 요일 → "0 45 * * * *"
     *
     * <p><b>zone = "Asia/Seoul"</b> 명시 이유:
     * JVM 기본 타임존이 UTC 인 운영 환경에서도 한국 시각 기준으로 동작하도록.
     * 명시하지 않으면 UTC 매시 45분 = KST 매시 45분 - 9시간 으로 어긋남.
     */
    @Scheduled(cron = "0 45 * * * *", zone = "Asia/Seoul")
    @CacheEvict(cacheNames = {
            StatsCacheNames.WEATHER_CORRELATION, StatsCacheNames.WEATHER_BY_TYPE,
            StatsCacheNames.WEATHER_BY_SIDO, StatsCacheNames.WEATHER_BY_SIGUNGU,
            StatsCacheNames.WEATHER_HOURLY_CORRELATION, StatsCacheNames.WEATHER_HOURLY_BY_TYPE,
            StatsCacheNames.WEATHER_HOURLY_BY_SIDO, StatsCacheNames.WEATHER_HOURLY_BY_SIGUNGU
    }, allEntries = true)
    public void runHourly() {
        log.info("WeatherCollectScheduler 시작");
        try {
            int saved = weatherCollectService.collectAndSave();
            log.info("WeatherCollectScheduler 완료: 적재={} rows", saved);
        } catch (Exception e) {
            // 다음 시각 사이클에 영향 안 주도록 여기서 잡음
            log.error("WeatherCollectScheduler 실패 — 다음 사이클에 재시도", e);
        }
    }
}
