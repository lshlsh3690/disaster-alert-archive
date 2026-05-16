package com.disaster.alert.alertapi.domain.weather.repository;

import com.disaster.alert.alertapi.domain.weather.model.WeatherObservation;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
