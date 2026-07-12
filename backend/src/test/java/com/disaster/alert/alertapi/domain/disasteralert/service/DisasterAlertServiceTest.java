package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.domain.disasteralert.dto.LatestAlertResponse;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import com.disaster.alert.alertapi.global.translation.DeepLTranslationClient;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * lang="en" 실제 번역(진짜 영어 텍스트가 오는지)은 real DeepL 호출이 필요하므로
 * {@link DisasterAlertServiceDeepLTranslationTest} 에서 별도로 검증한다.
 * 이 클래스는 DeepL을 mock 처리해 "번역 로직이 아예 안 타야 하는 경우(lang=ko)"의 배선만 검증.
 */
@IntegrationTest
class DisasterAlertServiceTest {

    @Autowired
    private DisasterAlertService disasterAlertService;

    @Autowired
    private DisasterAlertRepository disasterAlertRepository;

    @MockitoBean
    private DeepLTranslationClient deepLTranslationClient;

    @Test
    @Transactional
    void getLatestAlert_lang이_ko면_번역필드는_null이고_DeepL을_호출하지_않는다() {
        // when
        List<LatestAlertResponse> result = disasterAlertService.getLatestAlert(5, "ko");

        // then
        assertFalse(result.isEmpty(), "결과가 있어야 합니다.");
        LatestAlertResponse alert = result.get(0);

        assertNull(alert.getLanguage());
        assertNull(alert.getTranslatedMessage());
        assertNull(alert.getTranslatedDisasterType());

        verify(deepLTranslationClient, never()).translate(anyString(), anyString());
    }

    @Test
    @Transactional
    void saveData_전남광주통합특별시_지역명이_V108로_반영된_신규코드에_매핑된다() {
        // given: 2026-07-01 전남·광주 통합특별시 개편 이후 공공데이터포털이 내려주는 형식의 원본 응답
        // (legal_district는 V108__seed_jeonnam_gwangju_merged_legal_district.sql로 신규 코드가 반영되어 있다)
        String raw = """
                {
                  "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE", "errorMsg": ""},
                  "numOfRows": 3,
                  "pageNo": 1,
                  "totalCount": 3,
                  "body": [
                    {
                      "SN": 999999101,
                      "CRT_DT": "2026/07/09 04:00:48.000000000",
                      "MSG_CN": "테스트 - 담양군 호우 안내",
                      "RCPTN_RGN_NM": "전남광주통합특별시 담양군",
                      "EMRG_STEP_NM": "안전안내",
                      "DST_SE_NM": "호우",
                      "MDFCN_YMD": null
                    },
                    {
                      "SN": 999999102,
                      "CRT_DT": "2026/07/09 04:10:00.000000000",
                      "MSG_CN": "테스트 - 광주 북구 호우 안내",
                      "RCPTN_RGN_NM": "전남광주통합특별시 북구",
                      "EMRG_STEP_NM": "안전안내",
                      "DST_SE_NM": "호우",
                      "MDFCN_YMD": null
                    },
                    {
                      "SN": 999999103,
                      "CRT_DT": "2026/07/09 04:20:00.000000000",
                      "MSG_CN": "테스트 - 시도 전체 안내",
                      "RCPTN_RGN_NM": "전남광주통합특별시",
                      "EMRG_STEP_NM": "안전안내",
                      "DST_SE_NM": "호우",
                      "MDFCN_YMD": null
                    }
                  ]
                }
                """;

        // when
        List<Long> savedIds = disasterAlertService.saveData(raw);

        // then
        assertEquals(3, savedIds.size());

        DisasterAlert damyang = disasterAlertRepository.findById(savedIds.get(0)).orElseThrow();
        assertEquals(Set.of("1271000000"), districtCodes(damyang)); // 전남광주통합특별시 담양군

        DisasterAlert bukgu = disasterAlertRepository.findById(savedIds.get(1)).orElseThrow();
        assertEquals(Set.of("1230000000"), districtCodes(bukgu)); // 전남광주통합특별시 북구

        DisasterAlert sidoWide = disasterAlertRepository.findById(savedIds.get(2)).orElseThrow();
        assertEquals(Set.of("1200000000"), districtCodes(sidoWide)); // 전남광주통합특별시 전체
    }

    private Set<String> districtCodes(DisasterAlert alert) {
        return alert.getDisasterAlertRegions().stream()
                .map(region -> region.getId().getDistrictCode())
                .collect(Collectors.toSet());
    }
}