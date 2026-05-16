package com.disaster.alert.alertapi.domain.weather.model;

import com.disaster.alert.alertapi.domain.legaldistrict.model.LegalDistrict;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 시/군/구 단위 1시간 단위 날씨 관측/예보 엔티티.
 *
 * <p><b>역할</b>: 한 row = (시군구, 시각) 에 대한 1시간 표준 6변수 묶음.
 * 과거(ASOS) / 단기예보 / 초단기실황 데이터를 모두 한 테이블에 적재한다.
 *
 * <p><b>사용처</b>
 * <ul>
 *   <li>재난 정보 상세 응답에 "발령 시점 날씨" 함께 노출 (PR 4)</li>
 *   <li>AI 위험도 예측 모델의 입력 변수</li>
 * </ul>
 *
 * <p><b>UNIQUE (legal_district_code, observed_at)</b>
 * 같은 시군구 + 같은 시각 데이터는 1개만 존재. 백필 / cron 이 같은 데이터를 다시 INSERT 해도 안전.
 *
 * <p><b>모든 변수 nullable</b>
 * 관측소/예보 모델에 따라 결측 발생 가능. NULL 허용.
 */
@Entity
@Table(name = "weather_observation",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_wo_district_time",
               columnNames = {"legal_district_code", "observed_at"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class WeatherObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 시군구 코드 (FK → legal_district.code). 동 단위 아님. */
    @Column(name = "legal_district_code", length = 10, nullable = false)
    private String legalDistrictCode;

    /** 관측/예보 일시 (KST 가정). 1시간 단위로 적재. */
    @Column(name = "observed_at", nullable = false)
    private LocalDateTime observedAt;

    /** 기온 (°C). */
    private Double temperature;

    /** 강수량 (mm/h). */
    private Double precipitation;

    /** 풍속 (m/s). */
    @Column(name = "wind_speed")
    private Double windSpeed;

    /** 풍향 (deg, 0-360). */
    @Column(name = "wind_direction")
    private Integer windDirection;

    /** 상대습도 (%). */
    private Double humidity;

    /** 해면기압 (hPa). */
    private Double pressure;

    /** 데이터 출처. {@link WeatherSource}. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WeatherSource source;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 법정동 마스터 참조 (읽기 전용).
     * {@code legal_district_code} 컬럼은 직접 관리되므로 이쪽은 읽기용.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_district_code", insertable = false, updatable = false)
    private LegalDistrict legalDistrict;

    /**
     * 정적 팩토리 — 외부 API 응답 → 엔티티 변환.
     * created_at 은 {@code @CreatedDate} 가 자동 채움.
     */
    public static WeatherObservation of(
            String legalDistrictCode,
            LocalDateTime observedAt,
            Double temperature,
            Double precipitation,
            Double windSpeed,
            Integer windDirection,
            Double humidity,
            Double pressure,
            WeatherSource source
    ) {
        return WeatherObservation.builder()
                .legalDistrictCode(legalDistrictCode)
                .observedAt(observedAt)
                .temperature(temperature)
                .precipitation(precipitation)
                .windSpeed(windSpeed)
                .windDirection(windDirection)
                .humidity(humidity)
                .pressure(pressure)
                .source(source)
                .build();
    }
}
