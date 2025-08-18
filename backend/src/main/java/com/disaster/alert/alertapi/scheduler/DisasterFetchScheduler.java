package com.disaster.alert.alertapi.scheduler;

import com.disaster.alert.alertapi.api.DisasterOpenApiClient;
import com.disaster.alert.alertapi.domain.disasteralert.service.DisasterAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DisasterFetchScheduler {

    private final DisasterOpenApiClient openApiClient;
    private final DisasterAlertService alertService;
    // 매 10분마다 실행
    @Scheduled(cron = "0 0/10 * * * *")
    public void fetchAndSaveDisasterAlerts() {
        log.info("재난문자 수집 시작");

        String raw = openApiClient.fetchData();
        alertService.saveData(raw);
    }
}
