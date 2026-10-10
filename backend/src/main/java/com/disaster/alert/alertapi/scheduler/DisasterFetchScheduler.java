package com.disaster.alert.alertapi.scheduler;

import com.disaster.alert.alertapi.api.DisasterOpenApiClient;
import com.disaster.alert.alertapi.domain.disasteralert.service.DisasterAlertService;
import com.disaster.alert.alertapi.domain.event.service.EventClusteringService;
import com.disaster.alert.alertapi.domain.event.service.EventCrossRegionService;
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
    private final EventClusteringService eventClusteringService;
    private final EventCrossRegionService eventCrossRegionService;

    // 매 1분마다 실행 (2026-10-10 10분 → 1분). 한 사이클이 1분을 넘기면 cron 특성상 겹쳐 돌지 않고
    // 그 사이 지나간 실행 시점은 건너뛴다 — 스케줄러 스레드가 1개라 다른 @Scheduled 도 그동안 밀린다.
    @Scheduled(cron = "0 * * * * *")
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
            // 이벤트 클러스터링
            eventClusteringService.clusterNewAlert(alertId);
            // 기타(지역 이동 유형) cross-region LLM 병합
            eventCrossRegionService.linkCrossRegion(alertId);
        });
    }
}
