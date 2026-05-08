package com.disaster.alert.alertapi.global.translation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeepLTranslationClient {

    private final RestTemplate restTemplate;
    private final TranslationProperties properties;

    public String translate(String text, String targetLang) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", "DeepL-Auth-Key " + properties.getApiKey());

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
