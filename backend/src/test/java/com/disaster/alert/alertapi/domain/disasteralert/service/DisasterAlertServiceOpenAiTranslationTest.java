package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.domain.disasteralert.dto.LatestAlertResponse;
import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 번역 클라이언트를 mock 처리하지 않고 실제 OpenAI 를 호출해, lang별로 진짜 번역문이 오는지 검증한다.
 * OPENAI_API_KEY 가 없거나 유효하지 않으면 이 테스트가 그대로 실패해야 한다 — 배선만 확인하는
 * {@link DisasterAlertServiceTest} 와 달리 실제 번역 품질(적어도 "한글이 아님")까지 확인하는 목적.
 */
@IntegrationTest
@Slf4j
class DisasterAlertServiceOpenAiTranslationTest {

    @Autowired
    private DisasterAlertService disasterAlertService;

    @Test
    @Transactional
    void getLatestAlert_lang이_en이면_실제로_영어로_번역된다() {
        assertTranslated("en");
    }

    @Test
    @Transactional
    void getLatestAlert_lang이_ja이면_실제로_일본어로_번역된다() {
        assertTranslated("ja");
    }

    @Test
    @Transactional
    void getLatestAlert_lang이_zh이면_실제로_중국어로_번역된다() {
        assertTranslated("zh");
    }

    // VI/TH 는 라틴·알파시라빅 스크립트라 한글 대비 팽창률이 EN/JA/ZH 보다 크다. 번역문 길이
    // 가드(OpenAiTranslationClient.isSuspiciouslyLong)에 정상 번역이 걸리면 예외 → 원문 폴백이
    // 되어 translatedMessage 가 null 로 남으므로, assertTranslated 의 assertNotNull 이 잡아낸다.

    @Test
    @Transactional
    void getLatestAlert_lang이_vi이면_실제로_베트남어로_번역된다() {
        assertTranslated("vi");
    }

    @Test
    @Transactional
    void getLatestAlert_lang이_th이면_실제로_태국어로_번역된다() {
        assertTranslated("th");
    }

    private void assertTranslated(String lang) {
        List<LatestAlertResponse> result = disasterAlertService.getLatestAlert(5, lang);
        log.info("====결과 (lang={})====", lang);
        result.forEach(e -> {
            System.out.println("원본: " + e.getMessage());
            System.out.println("번역: " + e.getTranslatedMessage());
        });

        assertFalse(result.isEmpty(), "결과가 있어야 합니다.");
        LatestAlertResponse alert = result.get(0);

        assertEquals(lang, alert.getLanguage());
        // translation.enabled=false 이거나 번역이 실패하면 null 이 되는데, 아래 assertNotEquals·
        // containsHangul 은 null 을 통과시켜 테스트가 조용히 성공해버린다 — 먼저 null 을 막는다.
        assertNotNull(alert.getTranslatedMessage(), "번역문이 null 이면 안 됩니다(번역이 아예 안 탔는지 확인).");
        assertNotEquals(alert.getMessage(), alert.getTranslatedMessage(), "번역본이 원본과 같으면 안 됩니다.");
        assertFalse(containsHangul(alert.getTranslatedMessage()),
                "번역된 메시지에 한글이 남아있으면 안 됩니다: " + alert.getTranslatedMessage());
    }

    private boolean containsHangul(String text) {
        return text != null && text.matches("(?s).*\\p{IsHangul}.*");
    }
}
