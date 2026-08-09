package com.disaster.alert.alertapi.global.controller;

import com.disaster.alert.alertapi.domain.notification.service.AlertNotificationService;
import com.disaster.alert.alertapi.scheduler.DisasterFetchScheduler;
import com.disaster.alert.alertapi.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
// [시연용 임시] 무인증 호출을 허용하려고 잠시 끈다. 촬영 후 아래 주석을 해제하고
// SecurityConfig의 /api/v1/admin/** permitAll도 함께 제거할 것.
// @PreAuthorize("hasRole('ADMIN')")
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