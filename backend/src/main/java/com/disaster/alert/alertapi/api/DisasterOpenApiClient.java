package com.disaster.alert.alertapi.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@Log4j2
public class DisasterOpenApiClient {
    private final RestTemplate restTemplate;

    private final String URL = "https://www.safetydata.go.kr/V2/api/DSSP-IF-00247?serviceKey=";

    @Value("${disaster-alert.api.service-key}")
    private String serviceKey;


    public String fetchData() {
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        StringBuilder url = new StringBuilder();
        url.append(URL);
        url.append(serviceKey);
        url.append("&crtDt=" + date);
        url.append("&numOfRows=1000");

        try {
            String forObject = restTemplate.getForObject(url.toString(), String.class);
            log.info("Response from Disaster Open API fetched (length={}): {}", forObject != null ? forObject.length() : 0, "...");
            return forObject;
        } catch (Exception e) {
            log.warn("DisasterOpenApiClient.fetchData() 실패 - url={}", url );
            return null; // 장애 시 null 반환
        }
    }

    /**
     * crtDt 는 "해당 날짜 이후 전체"를 반환한다(단일 날짜 필터가 아님) — 과거 특정 기간을
     * 백필할 때 pageNo 로 전체를 끝까지 페이지네이션해야 totalCount 만큼 다 받는다.
     */
    public String fetchData(String crtDt, int pageNo, int numOfRows) {
        String url = URL + serviceKey + "&crtDt=" + crtDt + "&pageNo=" + pageNo + "&numOfRows=" + numOfRows;
        try {
            String forObject = restTemplate.getForObject(url, String.class);
            log.info("Response from Disaster Open API fetched (crtDt={}, pageNo={}, length={})",
                    crtDt, pageNo, forObject != null ? forObject.length() : 0);
            return forObject;
        } catch (Exception e) {
            log.warn("DisasterOpenApiClient.fetchData(crtDt={}, pageNo={}) 실패", crtDt, pageNo);
            return null;
        }
    }

    public String fetchData(int pageNo, int numOfRows) {
        String url = URL + serviceKey + "&pageNo=" + pageNo + "&numOfRows=" + numOfRows;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
            headers.set("Accept", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return response.getBody();
        } catch (Exception e) {
            log.warn("DisasterOpenApiClient.fetchData(page={}, size={}) 실패 - msg={}", pageNo, numOfRows, e.getMessage());
            return null;
        }
    }
}
