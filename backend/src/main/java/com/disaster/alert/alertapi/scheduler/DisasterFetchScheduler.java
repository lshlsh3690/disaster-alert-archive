package com.disaster.alert.alertapi.scheduler;

import com.disaster.alert.alertapi.api.DisasterOpenApiClient;
import com.disaster.alert.alertapi.domain.disasteralert.service.DisasterAlertService;
import com.disaster.alert.alertapi.domain.notification.service.AlertNotificationService;
import com.disaster.alert.alertapi.global.translation.TranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DisasterFetchScheduler {

    private final DisasterOpenApiClient openApiClient;
    private final DisasterAlertService alertService;
    private final TranslationService translationService;
    private final AlertNotificationService alertNotificationService;

    // 매 10분마다 실행
    @Scheduled(cron = "0 0/10 * * * *")
    public void fetchAndSaveDisasterAlerts() {
        log.info("재난문자 수집 시작");

        String raw = openApiClient.fetchData();
        if (raw == null || raw.isBlank()) {
            log.warn("외부 API 응답이 없거나 비어 있어 저장을 건너뜁니다.");
            return;
        }

        List<Long> newAlertIds = alertService.saveData(raw);

        newAlertIds.forEach(alertId -> {
            // 번역 비동기 처리
            translationService.translateAndSaveAsync(alertId);
            // FCM 알림 트리거
            alertNotificationService.triggerNotification(alertId);
        });
    }
}
