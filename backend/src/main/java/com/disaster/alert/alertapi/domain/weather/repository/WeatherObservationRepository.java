package com.disaster.alert.alertapi.domain.weather.repository;

import com.disaster.alert.alertapi.domain.weather.model.WeatherObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 날씨 관측 데이터 레포지토리.
 *
 * <p><b>주요 조회 경로</b>
 * <ul>
 *   <li>{@code findByLegalDistrictCodeAndObservedAt} — 재난 정보 응답 (PR 4) 의 핵심 경로.
 *       특정 시군구 × 특정 시각 1건 조회.</li>
 *   <li>{@code findByLegalDistrictCodeAndObservedAtBetween} — 대시보드/통계 시간 범위 조회.</li>
 *   <li>{@code existsByLegalDistrictCodeAndObservedAt} — 백필/cron 의 중복 적재 사전 차단.</li>
 * </ul>
 *
 * <p>이 메서드들은 모두 인덱스 {@code idx_wo_district_observed (legal_district_code, observed_at DESC)} 적중.
 */
public interface WeatherObservationRepository
        extends JpaRepository<WeatherObservation, Long> {

    /** 특정 시군구의 특정 시각 1건 조회 (재난 발령 시점 날씨 lookup 의 핵심 경로). */
    Optional<WeatherObservation> findByLegalDistrictCodeAndObservedAt(
            String legalDistrictCode, LocalDateTime observedAt);

    /** 시군구의 시간 범위 조회 (대시보드/통계용). */
    List<WeatherObservation> findByLegalDistrictCodeAndObservedAtBetween(
            String legalDistrictCode, LocalDateTime start, LocalDateTime end);

    /**
     * 백필/cron 의 중복 사전 차단용.
     * UNIQUE 제약이 DB 레벨에서 막아주지만, 미리 확인하면 불필요한 INSERT 예외 처리 회피.
     */
    boolean existsByLegalDistrictCodeAndObservedAt(
            String legalDistrictCode, LocalDateTime observedAt);

    /**
     * UPSERT — INSERT 시도 후 (legal_district_code, observed_at) 충돌 시 UPDATE.
     *
     * <p>cron 수집에서 사용. 같은 시각을 재시도하거나 수동 트리거가 중복 호출돼도 안전.
     * 또한 향후 백필/예보 등 다른 소스가 같은 시점을 채워둔 자리에 더 신뢰성 높은 데이터가
     * 들어올 때 자연스럽게 덮어쓰기.
     *
     * <p>PostgreSQL 의 {@code ON CONFLICT ... DO UPDATE} (UPSERT) 사용.
     * {@code EXCLUDED} 는 INSERT 하려던 새 값을 의미.
     *
     * <p><b>created_at</b> 은 NEW row 일 땐 NOW() 기본값으로 채워지고,
     * UPDATE 시엔 유지하기 위해 SET 절에서 제외.
     */
    @Modifying
    @Query(value = """
            INSERT INTO weather_observation (
                legal_district_code, observed_at,
                temperature, precipitation, wind_speed, wind_direction,
                humidity, pressure, source
            ) VALUES (
                :legalDistrictCode, :observedAt,
                :temperature, :precipitation, :windSpeed, :windDirection,
                :humidity, :pressure, :source
            )
            ON CONFLICT (legal_district_code, observed_at)
            DO UPDATE SET
                temperature    = EXCLUDED.temperature,
                precipitation  = EXCLUDED.precipitation,
                wind_speed     = EXCLUDED.wind_speed,
                wind_direction = EXCLUDED.wind_direction,
                humidity       = EXCLUDED.humidity,
                pressure       = EXCLUDED.pressure,
                source         = EXCLUDED.source
            """,
            nativeQuery = true)
    void upsert(
            @Param("legalDistrictCode") String legalDistrictCode,
            @Param("observedAt") LocalDateTime observedAt,
            @Param("temperature") Double temperature,
            @Param("precipitation") Double precipitation,
            @Param("windSpeed") Double windSpeed,
            @Param("windDirection") Integer windDirection,
            @Param("humidity") Double humidity,
            @Param("pressure") Double pressure,
            @Param("source") String source
    );
}
