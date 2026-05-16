package com.disaster.alert.alertapi.domain.weather.model;

/**
 * 날씨 관측 데이터의 출처.
 *
 * <p>한 {@code weather_observation} 행이 어느 소스에서 왔는지 표시한다.
 * 추후 신뢰도 평가, 충돌 처리, 디버깅에 사용.
 */
public enum WeatherSource {

    /**
     * 기상자료개방포털 ASOS 시간자료.
     * 종관기상관측소 (전국 102개) 의 확정된 과거 관측값.
     * 백필 (PR 2) 에서 사용.
     */
    ASOS_HISTORY,

    /**
     * 기상청 단기예보.
     * 격자(nx, ny) 단위, 3일 이내 미래 예보값.
     * 실시간 cron (PR 3) 에서 사용.
     */
    KMA_FORECAST,

    /**
     * 기상청 초단기실황.
     * 현재 시각 기준 관측 실황값.
     * 실시간 cron (PR 3) 에서 사용 (단기예보 보조용).
     */
    KMA_NOWCAST
}
