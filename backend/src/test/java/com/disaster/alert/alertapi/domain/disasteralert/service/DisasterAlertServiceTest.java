package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.domain.disasteralert.dto.LatestAlertResponse;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlertTranslation;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertTranslationRepository;
import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import com.disaster.alert.alertapi.global.translation.OpenAiTranslationClient;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 실제 번역 품질(한글이 남지 않는지, 기호가 보존되는지)은 real OpenAI 호출이 필요하므로
 * {@code OpenAiTranslationClientRealApiTest} 에서 별도로 검증한다.
 * 이 클래스는 번역 클라이언트를 mock 처리해 <b>배선</b>만 검증한다 — 번역을 아예 타지 않아야 하는
 * 경우(lang=ko)와, 실제로 타야 하는 경우(지원 언어)가 각각 DTO 까지 올바르게 이어지는지.
 */
@IntegrationTest
class DisasterAlertServiceTest {

    /** mock 번역 클라이언트가 돌려줄 고정 문자열 — DTO 까지 이 값이 그대로 실려 오는지로 배선을 확인한다. */
    private static final String STUB_TRANSLATION = "STUB_TRANSLATION";

    @Autowired
    private DisasterAlertService disasterAlertService;

    @Autowired
    private DisasterAlertRepository disasterAlertRepository;

    @Autowired
    private DisasterAlertTranslationRepository translationRepository;

    @MockitoBean
    private OpenAiTranslationClient translationClient;

    @Test
    @Transactional
    void getLatestAlert_lang이_ko면_번역필드는_null이고_번역API를_호출하지_않는다() {
        // when
        List<LatestAlertResponse> result = disasterAlertService.getLatestAlert(5, "ko");

        // then
        assertFalse(result.isEmpty(), "결과가 있어야 합니다.");
        LatestAlertResponse alert = result.get(0);

        assertNull(alert.getLanguage());
        assertNull(alert.getTranslatedMessage());
        assertNull(alert.getTranslatedDisasterType());

        verify(translationClient, never()).translate(anyString(), anyString());
    }

    @Test
    @Transactional
    void getLatestAlert_lang이_지원언어면_lazy번역결과가_DTO에_실린다() {
        // given: 최신 알림들의 EN 번역 캐시를 비워 lazy 번역 경로를 반드시 타게 한다.
        //        캐시가 남아 있으면 ensureTranslatedBatch 가 번역을 건너뛰어 배선 검증이 무의미해진다.
        //        @Transactional 이라 테스트 종료 시 삭제도 롤백된다.
        List<Long> latestIds = disasterAlertService.getLatestAlert(5, "ko").stream()
                .map(LatestAlertResponse::getId)
                .toList();
        List<DisasterAlertTranslation> cached =
                translationRepository.findByIdAlertIdInAndIdLanguageCode(latestIds, "EN");
        translationRepository.deleteAll(cached);
        translationRepository.flush();

        when(translationClient.translate(anyString(), eq("EN"))).thenReturn(STUB_TRANSLATION);

        // when
        List<LatestAlertResponse> result = disasterAlertService.getLatestAlert(5, "en");

        // then
        assertFalse(result.isEmpty(), "결과가 있어야 합니다.");
        LatestAlertResponse alert = result.get(0);

        assertEquals("en", alert.getLanguage());
        assertEquals(STUB_TRANSLATION, alert.getTranslatedMessage(),
                "번역 클라이언트 결과가 DTO 의 translatedMessage 까지 이어져야 합니다.");
        verify(translationClient, atLeastOnce()).translate(anyString(), eq("EN"));
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