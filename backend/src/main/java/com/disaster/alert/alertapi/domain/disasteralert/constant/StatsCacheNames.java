package com.disaster.alert.alertapi.domain.disasteralert.constant;

/**
 * /stats 페이지 집계 API들이 사용하는 Redis 캐시 이름.
 *
 * <p>새 재난문자 저장 시({@code DisasterAlertService#saveData}) 전체 evict,
 * 날씨 데이터 갱신 시({@code WeatherCollectScheduler}, {@code WeatherDailySummaryScheduler})
 * WEATHER_* 캐시만 evict한다.
 */
public final class StatsCacheNames {

    private StatsCacheNames() {
    }

    public static final String SUMMARY = "stats-summary";
    public static final String SIDO = "stats-sido";
    public static final String SIGUNGU = "stats-sigungu";
    public static final String DAILY = "stats-daily";
    public static final String HOURLY = "stats-hourly";
    public static final String MONTHLY_TYPE = "stats-monthly-type";
    public static final String DAILY_TYPE = "stats-daily-type";
    public static final String WEATHER_CORRELATION = "stats-weather-correlation";
    public static final String WEATHER_BY_TYPE = "stats-weather-by-type";
    public static final String WEATHER_BY_SIDO = "stats-weather-by-sido";
    public static final String WEATHER_BY_SIGUNGU = "stats-weather-by-sigungu";
    public static final String WEATHER_HOURLY_CORRELATION = "stats-weather-hourly-correlation";
    public static final String WEATHER_HOURLY_BY_TYPE = "stats-weather-hourly-by-type";
    public static final String WEATHER_HOURLY_BY_SIDO = "stats-weather-hourly-by-sido";
    public static final String WEATHER_HOURLY_BY_SIGUNGU = "stats-weather-hourly-by-sigungu";
}
