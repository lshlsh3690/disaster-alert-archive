package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.domain.disasteralert.dto.DisasterAlertResponseDto;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.legaldistrict.model.LegalDistrict;
import com.disaster.alert.alertapi.domain.legaldistrict.repository.LegalDistrictRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DisasterAlertServiceTest {

    @Autowired
    private DisasterAlertRepository disasterAlertRepository;

    @Autowired
    private DisasterAlertService disasterAlertService;

    @Autowired
    private LegalDistrictRepository legalDistrictRepository;

    @BeforeAll
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
        LegalDistrict legalDistrict = legalDistrictRepository.findByName("서울특별시")
                .orElseThrow(() -> new IllegalArgumentException("지역이 존재하지 않습니다."));
        LegalDistrict legalDistrict1 = legalDistrictRepository.findByName("광주광역시 동구")
                .orElseThrow(() -> new IllegalArgumentException("지역이 존재하지 않습니다."));


        // 테스트 데이터 추가
        DisasterAlert alert1 = DisasterAlert.builder()
                .sn(123456L)
                .message("호우 경보 발령")
                .createdAt(LocalDate.of(2024, 6, 1).atStartOfDay())
                .emergencyLevel(DisasterLevel.LEVEL_2)
                .disasterType("호우")
                .build();

        alert1.addRegion(legalDistrict); // 서울특별시 종로구 지역 추가


        DisasterAlert alert2 = DisasterAlert.builder()
                .sn(123457L)
                .message("지진 경보 발령")
                .createdAt(LocalDate.of(2024, 6, 2).atStartOfDay())
                .emergencyLevel(DisasterLevel.LEVEL_3)
                .disasterType("지진")
                .build();

        alert2.addRegion(legalDistrict1); // 광주광역시 동구 지역 추가

        disasterAlertRepository.saveAll(List.of(alert1, alert2));
    }

    @Test
    @Transactional
    void saveData() {
        List<DisasterAlert> all = disasterAlertRepository.findAll();

        assertEquals(2, all.size());
    }


    @Test
    @Transactional
    void searchAlerts_조건없이조회() {
        // given
        Pageable pageable = PageRequest.of(0, 10);

        // when
        Page<DisasterAlertResponseDto> result = disasterAlertService.searchAlerts(
                null, null, null, null, null, null, null, pageable
        );

        // then
        assertEquals(2, result.getTotalElements());
    }

    @Test
    @Transactional
    void searchAlerts_지역명기반조회() {
        Pageable pageable = PageRequest.of(0, 10);

        Page<DisasterAlertResponseDto> result = disasterAlertService.searchAlerts(
                "서울특별시", null, null, null, null, null, null, pageable
        );

        assertEquals(1, result.getTotalElements());
    }

    @Test
    @Transactional
    void searchAlerts_기간조회() {
        Pageable pageable = PageRequest.of(0, 10);

        Page<DisasterAlertResponseDto> result = disasterAlertService.searchAlerts(
                null, null,
                LocalDate.of(2024, 6, 2),
                LocalDate.of(2024, 6, 2),
                null, null, null, pageable
        );

        assertEquals(1, result.getTotalElements());
        assertEquals("지진 경보 발령", result.getContent().get(0).getMessage());
    }

    @Test
    @Transactional
    void searchAlerts_키워드조회() {
        Pageable pageable = PageRequest.of(0, 10);

        Page<DisasterAlertResponseDto> result = disasterAlertService.searchAlerts(
                null, null, null, null, null, null,
                "지진", pageable
        );

        assertEquals(1, result.getTotalElements());
        assertEquals("지진 경보 발령", result.getContent().get(0).getMessage());
    }

    @Test
    @Transactional
    void searchAlerts_종합조건조회() {
        Pageable pageable = PageRequest.of(0, 10);

        Page<DisasterAlertResponseDto> result = disasterAlertService.searchAlerts(
                "광주광역시 동구", "2911000000",
                LocalDate.of(2024, 6, 1),
                LocalDate.of(2024, 6, 2),
                "지진", DisasterLevel.LEVEL_3,
                "경보", pageable
        );

        assertEquals(1, result.getTotalElements());
        assertEquals("지진 경보 발령", result.getContent().get(0).getMessage());
    }
}