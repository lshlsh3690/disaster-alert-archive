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
        url.append("&crtDt=" + date.toString());
        url.append("&numOfRows=1000");

        String forObject = restTemplate.getForObject(url.toString(), String.class);
        log.info("Response from Disaster Open API: {}", forObject);
        return forObject;
    }

    public String fetchData(int pageNo, int numOfRows) {
        String url = URL + serviceKey + "&pageNo=" + pageNo + "&numOfRows=" + numOfRows;

        String forObject = restTemplate.getForObject(url, String.class);
        //log.info("Response from Disaster Open API for page {}: {}", pageNo, forObject);
        return forObject;
    }
}
