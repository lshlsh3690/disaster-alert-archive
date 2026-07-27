package com.disaster.alert.alertapi.global.translation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeepLTranslationClient {

    /** DeepL이 쿼터(평생 누적 한도) 초과 시 반환하는 상태 코드. 표준 HTTP 코드가 아니라 DeepL 고유 코드. */
    private static final int QUOTA_EXCEEDED_STATUS = 456;

    private final RestTemplate restTemplate;
    private final TranslationProperties properties;

    /**
     * 현재 사용 중인 키의 인덱스. 첫 키가 쿼터 초과로 막히면 다음 키로 넘어간 뒤 그 인덱스를
     * 계속 유지한다 — DeepL Free의 쿼터는 월간이 아니라 평생 누적 한도라 리셋되지 않으므로,
     * 매 요청마다 이미 막힌 걸 아는 키를 다시 시도해 실패 왕복을 반복할 필요가 없다.
     */
    private final AtomicInteger activeKeyIndex = new AtomicInteger(0);

    public String translate(String text, String targetLang) {
        List<String> apiKeys = apiKeys();
        int startIndex = Math.min(activeKeyIndex.get(), apiKeys.size() - 1);

        HttpClientErrorException lastQuotaError = null;
        for (int offset = 0; offset < apiKeys.size(); offset++) {
            int index = (startIndex + offset) % apiKeys.size();
            try {
                String result = callDeepL(text, targetLang, apiKeys.get(index));
                if (index != activeKeyIndex.get()) {
                    activeKeyIndex.set(index);
                    log.warn("DeepL API 키를 {}번째 키로 전환", index + 1);
                }
                return result;
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() != QUOTA_EXCEEDED_STATUS) {
                    throw e;
                }
                lastQuotaError = e;
                log.warn("DeepL {}번째 키 쿼터 초과, 다음 키로 재시도", index + 1);
            }
        }
        // 모든 키가 쿼터 초과 — 마지막 에러를 그대로 던져 호출 측(TranslationService)이 기존과
        // 동일하게 로그 남기고 스킵하도록 함.
        throw lastQuotaError;
    }

    private List<String> apiKeys() {
        List<String> keys = new ArrayList<>();
        if (StringUtils.hasText(properties.getApiKey())) {
            keys.add(properties.getApiKey());
        }
        if (StringUtils.hasText(properties.getApiKey2())) {
            keys.add(properties.getApiKey2());
        }
        if (keys.isEmpty()) {
            throw new IllegalStateException("deepl.api-key가 설정되어 있지 않습니다.");
        }
        return keys;
    }

    private String callDeepL(String text, String targetLang, String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", "DeepL-Auth-Key " + apiKey);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("text", text);
        body.add("source_lang", "KO");
        body.add("target_lang", targetLang);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                properties.getApiUrl(),
                HttpMethod.POST,
                request,
                Map.class
        );

        List<Map<String, String>> translations = (List<Map<String, String>>) response.getBody().get("translations");
        return translations.get(0).get("text");
    }
}
