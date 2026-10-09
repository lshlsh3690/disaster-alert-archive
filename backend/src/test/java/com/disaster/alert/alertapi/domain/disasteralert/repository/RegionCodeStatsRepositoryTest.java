package com.disaster.alert.alertapi.domain.disasteralert.repository;

import com.disaster.alert.alertapi.domain.disasteralert.dto.AlertSearchRequest;
import com.disaster.alert.alertapi.domain.disasteralert.dto.DisasterAlertStatResponse;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.disaster.alert.alertapi.domain.disasteralert.model.RegionCodeUnit;
import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 대시보드 지도용 코드 기준 통계({@code getStatsByRegionCode})를 실제 DB로 검증한다.
 *
 * <p>기존 데이터와 섞이지 않도록 1999년 1월 날짜 구간으로만 조회하고, 모든 데이터는 테스트 트랜잭션과 함께 롤백된다.
 * 법정동 코드는 legal_district 시드에 있는 값을 쓴다(FK).
 */
@IntegrationTest
@Transactional
class RegionCodeStatsRepositoryTest {

    private static final LocalDate START = LocalDate.of(1999, 1, 1);
    private static final LocalDate END = LocalDate.of(1999, 1, 31);

    // 서울 종로구(구 전체/시도 전체/하위 읍면동), 수원시 장안구(구가 있는 시), 강화읍(리 단위)
    private static final String SEOUL_SIDO = "1100000000";
    private static final String JONGNO = "1111000000";
    private static final String JONGNO_CHEONGUN = "1111010100";
    private static final String JONGNO_SINGYO = "1111010200";
    private static final String SUWON_SI = "4111000000";
    private static final String SUWON_JANGAN_PAJANG = "4111112900";
    private static final String GANGHWA_RI_SINMUN = "2871025021";
    private static final String GANGHWA_RI_GWANCHEONG = "2871025022";

    @Autowired
    private DisasterAlertRepository disasterAlertRepository;

    private long sn = 990_000_000L;

    @BeforeEach
    void 데이터_준비() {
        // 종로구 청운동+신교동 (같은 시군구의 읍면동 2개를 한 알림이 가리킴)
        save(LocalDateTime.of(1999, 1, 10, 9, 0), JONGNO_CHEONGUN, JONGNO_SINGYO);
        // 종로구 전체 — 읍면동까지 특정되지 않음
        save(LocalDateTime.of(1999, 1, 11, 9, 0), JONGNO);
        // 서울 전체(시도 전체 코드) — 시군구/읍면동 어디에도 귀속 불가
        save(LocalDateTime.of(1999, 1, 12, 9, 0), SEOUL_SIDO);
        // 수원시 장안구 파장동 / 수원시 전체
        save(LocalDateTime.of(1999, 1, 13, 9, 0), SUWON_JANGAN_PAJANG);
        save(LocalDateTime.of(1999, 1, 14, 9, 0), SUWON_SI);
        // 강화읍의 리 두 곳(10자리) — 읍면동(8자리)으로 합산되고 알림은 1건으로 센다
        save(LocalDateTime.of(1999, 1, 15, 9, 0), GANGHWA_RI_SINMUN, GANGHWA_RI_GWANCHEONG);
        // 조회 기간 밖(2월) — 집계에서 빠져야 한다
        save(LocalDateTime.of(1999, 2, 15, 9, 0), JONGNO_CHEONGUN);
        disasterAlertRepository.flush();
    }

    @Test
    void 시군구_단위는_코드_앞_5자리로_묶고_시도_전체_코드는_제외한다() {
        Map<String, Long> counts = counts(RegionCodeUnit.SIGUNGU);

        // 종로구: 청운동+신교동 알림(1건, distinct) + 종로구 전체 알림(1건)
        assertEquals(2L, counts.get("11110"));
        // 구가 있는 시는 구 단위로 나뉜다: 장안구(41111) 와 수원시 전체(41110) 는 서로 다른 그룹
        assertEquals(1L, counts.get("41111"));
        assertEquals(1L, counts.get("41110"));
        // 강화군: 리 두 곳을 가리킨 알림 1건
        assertEquals(1L, counts.get("28710"));
        // 서울 전체(시도 코드)는 어느 시군구에도 귀속되지 않는다. 기간 밖(2월) 알림도 빠진다.
        assertEquals(4, counts.size(), "집계 대상 시군구는 종로·장안·수원시·강화 4곳뿐이어야 한다: " + counts);
    }

    @Test
    void 읍면동_단위는_8자리로_묶고_시군구_전체는_뒤_000_코드로_내려주며_시도_전체는_제외한다() {
        Map<String, Long> counts = counts(RegionCodeUnit.EMD);

        assertEquals(1L, counts.get("11110101"), "종로구 청운동");
        assertEquals(1L, counts.get("11110102"), "종로구 신교동");
        assertEquals(1L, counts.get("41111129"), "수원시 장안구 파장동");
        // 같은 읍의 리 두 곳을 가리킨 알림은 읍면동 한 곳에 1건으로 센다(distinct)
        assertEquals(1L, counts.get("28710250"), "강화읍");
        // 읍면동까지 특정되지 않은 시군구 전체 알림은 어느 읍면동에도 귀속시킬 수 없어 "시군구코드+000" 항목으로 따로 내려준다.
        // 화면은 이 값을 읍면동 건수와 섞지 않고 "○○ 전체 단위 알림 N건"으로 보여 준다.
        assertEquals(1L, counts.get("11110000"), "종로구 전체");
        assertEquals(1L, counts.get("41110000"), "수원시 전체");
        // 서울 전체(시도 전체 코드)는 시군구 전체 단위조차 아니라서 여전히 제외된다
        assertEquals(6, counts.size(), "읍면동 4곳 + 시군구 전체 2곳이어야 한다: " + counts);
    }

    @Test
    void 기간_밖_알림은_집계하지_않는다() {
        AlertSearchRequest februaryOnly = AlertSearchRequest.builder()
                .startDate(LocalDate.of(1999, 2, 1)).endDate(LocalDate.of(1999, 2, 28)).build();

        Map<String, Long> counts = disasterAlertRepository.getStatsByRegionCode(februaryOnly, RegionCodeUnit.EMD)
                .stream().collect(Collectors.toMap(
                        DisasterAlertStatResponse.RegionCodeStat::getCode,
                        DisasterAlertStatResponse.RegionCodeStat::getCount));

        assertEquals(Map.of("11110101", 1L), counts);
    }

    private Map<String, Long> counts(RegionCodeUnit unit) {
        AlertSearchRequest january = AlertSearchRequest.builder().startDate(START).endDate(END).build();
        return disasterAlertRepository.getStatsByRegionCode(january, unit).stream()
                .collect(Collectors.toMap(
                        DisasterAlertStatResponse.RegionCodeStat::getCode,
                        DisasterAlertStatResponse.RegionCodeStat::getCount,
                        Long::sum, java.util.LinkedHashMap::new));
    }

    private void save(LocalDateTime createdAt, String... districtCodes) {
        DisasterAlert alert = DisasterAlert.builder()
                .sn(sn++)
                .message("코드 기준 통계 테스트")
                .createdAt(createdAt)
                .emergencyLevel(DisasterLevel.LEVEL_1)
                .disasterType("테스트")
                .modifiedDate(createdAt)
                .originalRegion("테스트")
                .build();
        for (String code : districtCodes) {
            alert.addRegionCode(code);
        }
        disasterAlertRepository.save(alert);
    }
}
