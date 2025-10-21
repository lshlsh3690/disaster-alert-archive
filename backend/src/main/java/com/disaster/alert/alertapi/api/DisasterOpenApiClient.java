package com.disaster.alert.alertapi.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
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
            log.warn("DisasterOpenApiClient.fetchData() 실패 - url={}, msg={}", url, e.getMessage());
            return null; // 장애 시 null 반환
        }
    }

    public String fetchData(int pageNo, int numOfRows) {
        String url = URL + serviceKey + "&pageNo=" + pageNo + "&numOfRows=" + numOfRows;
        try {
            String forObject = restTemplate.getForObject(url, String.class);
            return forObject;
        } catch (Exception e) {
            log.warn("DisasterOpenApiClient.fetchData(page={}, size={}) 실패 - msg={} ", pageNo, numOfRows, e.getMessage());
            return null;
        }
    }
}
