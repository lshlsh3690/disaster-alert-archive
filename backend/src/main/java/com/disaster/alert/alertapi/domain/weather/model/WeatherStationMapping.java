package com.disaster.alert.alertapi.domain.weather.model;

import com.disaster.alert.alertapi.domain.legaldistrict.model.LegalDistrict;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시/군/구 ↔ 기상 관측 소스(과거 ASOS / 단기예보 격자) 매핑 엔티티.
 *
 * <p><b>역할</b>: 한 시군구가
 * <ul>
 *   <li>과거 데이터를 어느 ASOS 지점에서 가져올지 ({@code asosStationId})</li>
 *   <li>단기예보를 어느 격자에서 가져올지 ({@code kmaNx}, {@code kmaNy})</li>
 * </ul>
 * 를 정의한다.
 *
 * <p><b>설계 결정</b>
 * <ul>
 *   <li>PK = {@code legalDistrictCode} (시군구 단독). 한 시군구가 매핑 1개를 가짐.</li>
 *   <li>ASOS 지점은 약 102개뿐이라 시군구당 1:1 안 됨 → 인접 시군구 여러 개가 같은 지점 공유.
 *       예: 서울 강남구/강북구는 모두 ASOS 지점 "108"(서울) 사용.</li>
 *   <li>FK 는 {@code legal_district.code} 로 마스터 데이터 정합성 유지.</li>
 * </ul>
 */
@Entity
@Table(name = "weather_station_mapping")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class WeatherStationMapping {

    /** 시군구 코드 (예: "1168000000" = 서울 강남구). PR 1 의 V8 시드에서 적재. */
    @Id
    @Column(name = "legal_district_code", length = 10)
    private String legalDistrictCode;

    /** 기상자료개방포털 ASOS 지점번호 (예: "108" = 서울). */
    @Column(name = "asos_station_id", length = 10)
    private String asosStationId;

    /** ASOS 지점명 (표시/디버깅용. 예: "서울", "부산"). */
    @Column(name = "asos_station_name", length = 50)
    private String asosStationName;

    /** 기상청 단기예보 격자 X 좌표. */
    @Column(name = "kma_nx")
    private Integer kmaNx;

    /** 기상청 단기예보 격자 Y 좌표. */
    @Column(name = "kma_ny")
    private Integer kmaNy;

    /**
     * 법정동 마스터 참조 (읽기 전용).
     * {@code legal_district_code} 컬럼은 {@code @Id} 가 이미 관리하므로
     * 이쪽 {@code @ManyToOne} 은 {@code insertable=false, updatable=false} 로 둔다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_district_code", insertable = false, updatable = false)
    private LegalDistrict legalDistrict;

    /** 외부 자료 → 엔티티 변환용 정적 팩토리. */
    public static WeatherStationMapping of(
            String legalDistrictCode,
            String asosStationId,
            String asosStationName,
            Integer kmaNx,
            Integer kmaNy
    ) {
        return WeatherStationMapping.builder()
                .legalDistrictCode(legalDistrictCode)
                .asosStationId(asosStationId)
                .asosStationName(asosStationName)
                .kmaNx(kmaNx)
                .kmaNy(kmaNy)
                .build();
    }
}
