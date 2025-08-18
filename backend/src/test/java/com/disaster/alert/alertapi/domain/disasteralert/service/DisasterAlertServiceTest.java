package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.domain.disasteralert.dto.AlertSearchRequest;
import com.disaster.alert.alertapi.domain.disasteralert.dto.DisasterAlertResponseDto;
import com.disaster.alert.alertapi.domain.disasteralert.dto.DisasterAlertStatResponse;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.legaldistrict.model.LegalDistrict;
import com.disaster.alert.alertapi.domain.legaldistrict.repository.LegalDistrictRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Slf4j
class DisasterAlertServiceTest {

    @Autowired
    private DisasterAlertRepository disasterAlertRepository;

    @Autowired
    private DisasterAlertService disasterAlertService;

    @Autowired
    private LegalDistrictRepository legalDistrictRepository;

    @BeforeEach
    @Transactional
    public void setUpLegalDistrict() {
        // 테스트용 지역 데이터 추가
        LegalDistrict legalDistrict = LegalDistrict.builder()
                .name("서울특별시")
                .code("1100000000")
                .isActiveString("존재")
                .build();
        legalDistrictRepository.save(legalDistrict);

        LegalDistrict jongro = LegalDistrict.builder()
                .name("서울특별시 종로구")
                .code("1111000000")
                .isActiveString("존재")
                .build();
        legalDistrictRepository.save(jongro);

        LegalDistrict gwangju = LegalDistrict.builder()
                .name("광주광역시")
                .code("2900000000")
                .isActiveString("존재")
                .build();
        legalDistrictRepository.save(gwangju);

        LegalDistrict gwangjuDongu = LegalDistrict.builder()
                .name("광주광역시 동구")
                .code("2911000000")
                .isActiveString("존재")
                .build();

        legalDistrictRepository.save(gwangjuDongu);
    }

    @BeforeEach
    @Transactional
    void setUp() {
        LegalDistrict seoul = legalDistrictRepository.findByName("서울특별시")
                .orElseThrow(() -> new IllegalArgumentException("지역이 존재하지 않습니다."));
        LegalDistrict jongro = legalDistrictRepository.findByName("서울특별시 종로구")
                .orElseThrow(() -> new IllegalArgumentException("지역이 존재하지 않습니다."));
        LegalDistrict gwangjuDongu = legalDistrictRepository.findByName("광주광역시 동구")
                .orElseThrow(() -> new IllegalArgumentException("지역이 존재하지 않습니다."));
        LegalDistrict gwangju = legalDistrictRepository.findByName("광주광역시")
                .orElseThrow(() -> new IllegalArgumentException("지역이 존재하지 않습니다."));
        // 테스트 데이터 추가
        // alert1 - 서울특별시, 종로구
        DisasterAlert alert1 = DisasterAlert.builder()
                .sn(123456L)
                .message("호우 경보 발령")
                .createdAt(LocalDate.of(2024, 6, 1).atStartOfDay())
                .emergencyLevel(DisasterLevel.LEVEL_2)
                .disasterType("호우")
                .build();
        alert1.addRegion(seoul);
        alert1.addRegion(jongro);

        // alert2 - 광주광역시 동구
        DisasterAlert alert2 = DisasterAlert.builder()
                .sn(123457L)
                .message("지진 경보 발령")
                .createdAt(LocalDate.of(2024, 6, 2).atStartOfDay())
                .emergencyLevel(DisasterLevel.LEVEL_3)
                .disasterType("지진")
                .build();
        alert2.addRegion(gwangjuDongu);

        // alert3 - 광주광역시
        DisasterAlert alert3 = DisasterAlert.builder()
                .sn(123458L)
                .message("폭염 경보 발령")
                .createdAt(LocalDate.of(2024, 6, 3).atStartOfDay())
                .emergencyLevel(DisasterLevel.LEVEL_1)
                .disasterType("폭염")
                .build();
        alert3.addRegion(gwangju);

        disasterAlertRepository.saveAll(List.of(alert1, alert2, alert3));
    }

    @Test
    @Transactional
    void saveData() {
        String rawData = """
                {
                    "header": {
                        "resultMsg": "NORMAL SERVICE",
                        "resultCode": "00",
                        "errorMsg": null
                    },
                    "numOfRows": 1000,
                    "pageNo": 1,
                    "totalCount": 5,
                    "body": [
                        {
                            "MSG_CN": "6. 25.(수) 14시경 백령 및 연평도 지역에서 우리 군이 해상사격 예정입니다. 주민 및 방문객들은 야외활동을 자제바랍니다. [옹진군]",
                            "RCPTN_RGN_NM": "인천광역시 옹진군 백령면,인천광역시 옹진군 대청면,인천광역시 옹진군 연평면",
                            "CRT_DT": "2025/06/25 13:09:14",
                            "REG_YMD": "2025/06/25 13:09:20.000000000",
                            "EMRG_STEP_NM": "안전안내",
                            "SN": 237140,
                            "DST_SE_NM": "기타",
                            "MDFCN_YMD": "2025/06/25 13:18:50.000000000"
                        },
                        {
                            "MSG_CN": "최근 공무원을 사칭하여 물품구매를 유도하는 등 사기 사례가 발생하고 있습니다. 김제시청은 계약관련 금전 및 물품을 요구하지 않으니 주의해 주시기 바랍니다[김제시]",
                            "RCPTN_RGN_NM": "전북특별자치도 김제시 ",
                            "CRT_DT": "2025/06/25 14:09:48",
                            "REG_YMD": "2025/06/25 14:09:50.000000000",
                            "EMRG_STEP_NM": "안전안내",
                            "SN": 237141,
                            "DST_SE_NM": "기타",
                            "MDFCN_YMD": "2025/06/25 14:12:50.000000000"
                        },
                        {
                            "MSG_CN": "2025-06-25 05:59 충북 증평군 북북동쪽 7km 지역 규모 2.2 지진발생/추가 지진 발생상황에 유의 바람 [기상청]",
                            "RCPTN_RGN_NM": "충청남도 천안시 ,충청북도 괴산군 ,충청북도 음성군 ,충청북도 진천군 ,충청북도 청주시 ,충청북도 충주시 ,충청북도 증평군 ",
                            "CRT_DT": "2025/06/25 06:03:02",
                            "REG_YMD": "2025/06/25 06:03:10.000000000",
                            "EMRG_STEP_NM": "안전안내",
                            "SN": 237138,
                            "DST_SE_NM": "지진",
                            "MDFCN_YMD": "2025/06/25 06:12:50.000000000"
                        },
                        {
                            "MSG_CN": "금일(25일)부터 대조기 기간에 따라 최대 조위가 512cm(새벽 시간대)까지 상승이 예상되오니, ▲ 위험지역 접근자제 등 안전에 유의하시기 바랍니다.[신안군]",
                            "RCPTN_RGN_NM": "전라남도 신안군 ",
                            "CRT_DT": "2025/06/25 08:59:18",
                            "REG_YMD": "2025/06/25 08:59:20.000000000",
                            "EMRG_STEP_NM": "안전안내",
                            "SN": 237139,
                            "DST_SE_NM": "환경오염사고",
                            "MDFCN_YMD": "2025/06/25 09:08:50.000000000"
                        },
                        {
                            "MSG_CN": "현재 신현4통 상태마을 전지역에 정상적으로 상수도 공급이 이루어지고 있으나, 탁수가 일부 나올 수 있음으로 생활용수 외 음용수로 사용 시 확인 바람.[광주시]",
                            "RCPTN_RGN_NM": "경기도 광주시 신현동",
                            "CRT_DT": "2025/06/25 05:51:25",
                            "REG_YMD": "2025/06/25 05:51:30.000000000",
                            "EMRG_STEP_NM": "안전안내",
                            "SN": 237137,
                            "DST_SE_NM": "수도",
                            "MDFCN_YMD": "2025/06/25 06:00:50.000000000"
                        }
                    ]
                }
                """;

        disasterAlertService.saveData(rawData);

    }


    @Test
    @Transactional
    void searchAlerts_조건없이조회() {
        // given
        Pageable pageable = PageRequest.of(0, 10);

        AlertSearchRequest request = AlertSearchRequest.builder()
                .region(null)
                .districtCode(null)
                .startDate(null)
                .endDate(null)
                .type(null)
                .level(null)
                .keyword(null)
                .build();
        // when
        Page<DisasterAlertResponseDto> result = disasterAlertService.searchAlerts(
                request, pageable
        );

        // then
        assertEquals(2, result.getTotalElements());
    }

    @Test
    @Transactional
    void searchAlerts_지역명기반조회() {
        Pageable pageable = PageRequest.of(0, 10);


        // 서울특별시 지역명으로 조회
        AlertSearchRequest request = AlertSearchRequest.builder()
                .region("서울특별시")
                .districtCode(null)
                .startDate(null)
                .endDate(null)
                .type(null)
                .level(null)
                .keyword(null)
                .build();

        Page<DisasterAlertResponseDto> result = disasterAlertService.searchAlerts(
                request, pageable
        );

        assertEquals(1, result.getTotalElements());
    }

    @Test
    @Transactional
    void searchAlerts_기간조회() {
        Pageable pageable = PageRequest.of(0, 10);


        // 2024년 6월 2일에 발생한 재난 경고 조회
        AlertSearchRequest request = AlertSearchRequest.builder()
                .region(null)
                .districtCode(null)
                .startDate(LocalDate.of(2024, 6, 2))
                .endDate(LocalDate.of(2024, 6, 2))
                .type(null)
                .level(null)
                .keyword(null)
                .build();

        Page<DisasterAlertResponseDto> result = disasterAlertService.searchAlerts(
                request, pageable
        );

        assertEquals(1, result.getTotalElements());
        assertEquals("지진 경보 발령", result.getContent().get(0).getMessage());
    }

    @Test
    @Transactional
    void searchAlerts_키워드조회() {
        Pageable pageable = PageRequest.of(0, 10);

        // "지진" 키워드로 조회
        AlertSearchRequest request = AlertSearchRequest.builder()
                .region(null)
                .districtCode(null)
                .startDate(null)
                .endDate(null)
                .type("지진")
                .level(null)
                .keyword(null)
                .build();
        Page<DisasterAlertResponseDto> result = disasterAlertService.searchAlerts(
                request, pageable
        );

        assertEquals(1, result.getTotalElements());
        assertEquals("지진 경보 발령", result.getContent().get(0).getMessage());
    }

    @Test
    @Transactional
    void searchAlerts_종합조건조회() {
        Pageable pageable = PageRequest.of(0, 10);

        // 종합 조건으로 조회
        AlertSearchRequest request = AlertSearchRequest.builder()
                .region("광주광역시 동구")
                .districtCode("2911000000")
                .startDate(LocalDate.of(2024, 6, 1))
                .endDate(LocalDate.of(2024, 6, 2))
                .type("지진")
                .level(DisasterLevel.LEVEL_3)
                .keyword("경보")
                .build();
        Page<DisasterAlertResponseDto> result = disasterAlertService.searchAlerts(
                request, pageable
        );

        assertEquals(1, result.getTotalElements());
        assertEquals("지진 경보 발령", result.getContent().get(0).getMessage());
    }

    @Test
    @Transactional
    void getStats_조건없이조회() {
        DisasterAlertStatResponse stats = disasterAlertService.getStats(null, null, null, null, null, null, null);

        assertEquals(2, stats.getTotalCount());
        assertEquals(2, stats.getRegionStats().size());
        assertEquals("서울특별시", stats.getRegionStats().get(0).getRegion());
        assertEquals(1, stats.getRegionStats().get(0).getCount());
        assertEquals(2, stats.getLevelStats().size());
        assertEquals(DisasterLevel.LEVEL_3, stats.getLevelStats().get(0).getLevel());
        assertEquals(1, stats.getLevelStats().get(0).getCount());
    }

    @Test
    @Transactional
    void getStats_지역기반조회() {
        DisasterAlertStatResponse stats = disasterAlertService.getStats("서울특별시", null, null, null, null, null, null);

        assertEquals(1, stats.getTotalCount());
        assertEquals(1, stats.getRegionStats().size());
        assertEquals("서울특별시", stats.getRegionStats().get(0).getRegion());
        assertEquals(1, stats.getRegionStats().get(0).getCount());
    }

    @Test
    @Transactional
    void getStats_기간조회() {
        DisasterAlertStatResponse stats = disasterAlertService.getStats(null, null,
                LocalDate.of(2024, 6, 2), LocalDate.of(2024, 6, 2), null, null, null);

        assertEquals(1, stats.getTotalCount());
        assertEquals(1, stats.getRegionStats().size());
        assertEquals("광주광역시 동구", stats.getRegionStats().get(0).getRegion());
        assertEquals(1, stats.getRegionStats().get(0).getCount());
    }

    @Test
    @Transactional
    void getStats_키워드조회() {
        DisasterAlertStatResponse stats = disasterAlertService.getStats(null, null, null, null, "지진", null, null);

        assertEquals(1, stats.getTotalCount());
        assertEquals(1, stats.getTypeStats().size());
        assertEquals("지진", stats.getTypeStats().get(0).getType());
        assertEquals(1, stats.getTypeStats().get(0).getCount());
    }

    @Test
    @Transactional
    void getStats_종합조건조회() {
        DisasterAlertStatResponse stats = disasterAlertService.getStats(
                "광주광역시 동구", "2911000000",
                LocalDate.of(2024, 6, 1), LocalDate.of(2024, 6, 2),
                "지진", DisasterLevel.LEVEL_3, null
        );

        assertEquals(1, stats.getTotalCount());
        assertEquals(1, stats.getRegionStats().size());
        assertEquals("광주광역시 동구", stats.getRegionStats().get(0).getRegion());
        assertEquals(1, stats.getRegionStats().get(0).getCount());
        assertEquals(1, stats.getLevelStats().size());
        assertEquals(DisasterLevel.LEVEL_3, stats.getLevelStats().get(0).getLevel());
        assertEquals(1, stats.getLevelStats().get(0).getCount());
    }

}