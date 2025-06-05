package com.disaster.alert.alertapi.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Log4j2
public class DisasterOpenApiClient {
    private final RestTemplate restTemplate;

    private final String URL = "https://www.safetydata.go.kr/V2/api/DSSP-IF-00247?serviceKey=";

    @Value("${disaster-alert.api.service-key}")
    private String serviceKey;


    public String fetchData() {
        String url = URL + serviceKey;

        String forObject = restTemplate.getForObject(url, String.class);
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
