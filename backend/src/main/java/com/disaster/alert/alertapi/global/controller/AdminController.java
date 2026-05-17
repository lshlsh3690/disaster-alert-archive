package com.disaster.alert.alertapi.global.controller;

import com.disaster.alert.alertapi.domain.notification.service.AlertNotificationService;
import com.disaster.alert.alertapi.scheduler.DisasterFetchScheduler;
import com.disaster.alert.alertapi.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
@Profile("!prod") // 운영 환경에서는 비활성화
public class AdminController {

    private final DisasterFetchScheduler disasterFetchScheduler;
    private final AlertNotificationService alertNotificationService;


    // 재난문자 수집 + FCM 알림 수동 트리거
    @PostMapping("/trigger-fetch")
    public ResponseEntity<ApiResponse<String>> triggerFetch() {
        disasterFetchScheduler.fetchAndSaveDisasterAlerts();
        return ResponseEntity.ok(ApiResponse.success("재난문자 수집 및 알림 트리거 완료"));
    }

    @PostMapping("/trigger-notification/{alertId}")
    public ResponseEntity<ApiResponse<String>> triggerNotification(
            @PathVariable Long alertId) {
        alertNotificationService.triggerNotification(alertId);
        return ResponseEntity.ok(ApiResponse.success("알림 트리거 완료"));
    }
}