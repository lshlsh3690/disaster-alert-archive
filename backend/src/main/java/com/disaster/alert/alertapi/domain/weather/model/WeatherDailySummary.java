package com.disaster.alert.alertapi.domain.weather.model;

import com.disaster.alert.alertapi.domain.legaldistrict.model.LegalDistrict;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "weather_daily_summary",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_wds_district_date",
               columnNames = {"legal_district_code", "date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class WeatherDailySummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "legal_district_code", length = 10, nullable = false)
    private String legalDistrictCode;

    @Column(nullable = false)
    private LocalDate date;

    // 기온 (°C)
    @Column(name = "avg_temp")
    private Double avgTemp;

    @Column(name = "min_temp")
    private Double minTemp;

    @Column(name = "max_temp")
    private Double maxTemp;

    // 강수 (mm)
    @Column(name = "total_precip")
    private Double totalPrecip;

    @Column(name = "max_hourly_precip")
    private Double maxHourlyPrecip;

    @Column(name = "precip_hours")
    private Integer precipHours;

    // 풍속 (m/s)
    @Column(name = "avg_wind_speed")
    private Double avgWindSpeed;

    @Column(name = "max_wind_speed")
    private Double maxWindSpeed;

    // 습도 (%)
    @Column(name = "avg_humidity")
    private Double avgHumidity;

    @Column(name = "min_humidity")
    private Double minHumidity;

    // 기압 (hPa)
    @Column(name = "avg_pressure")
    private Double avgPressure;

    @Column(name = "obs_count", nullable = false)
    private int obsCount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_district_code", insertable = false, updatable = false)
    private LegalDistrict legalDistrict;
}
