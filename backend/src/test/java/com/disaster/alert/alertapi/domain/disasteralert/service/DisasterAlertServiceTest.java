package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.domain.disasteralert.dto.LatestAlertResponse;
import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import com.disaster.alert.alertapi.global.translation.DeepLTranslationClient;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

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
}