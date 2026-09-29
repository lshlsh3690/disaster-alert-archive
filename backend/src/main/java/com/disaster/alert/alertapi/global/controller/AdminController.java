package com.disaster.alert.alertapi.global.controller;

import com.disaster.alert.alertapi.scheduler.DisasterFetchScheduler;
import com.disaster.alert.alertapi.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final DisasterFetchScheduler disasterFetchScheduler;

    // 재난문자 수집 + FCM 알림 수동 트리거
    @PostMapping("/trigger-fetch")
    public ResponseEntity<ApiResponse<String>> triggerFetch() {
        disasterFetchScheduler.fetchAndSaveDisasterAlerts();
        return ResponseEntity.ok(ApiResponse.success("재난문자 수집 및 알림 트리거 완료"));
    }
}