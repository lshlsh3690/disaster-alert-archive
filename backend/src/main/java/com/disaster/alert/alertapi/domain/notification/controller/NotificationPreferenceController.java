package com.disaster.alert.alertapi.domain.notification.controller;

import com.disaster.alert.alertapi.domain.notification.dto.NotificationPreferenceDtos;
import com.disaster.alert.alertapi.domain.notification.service.NotificationPreferenceService;
import com.disaster.alert.alertapi.global.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notification-preference")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    // 현재 알림 설정 조회
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<ApiResponse<NotificationPreferenceDtos.Response>> getPreference(
            @AuthenticationPrincipal(expression = "id") Long memberId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(preferenceService.getPreference(memberId))
        );
    }

    // 알림 타입 변경
    @PreAuthorize("isAuthenticated()")
    @PutMapping
    public ResponseEntity<ApiResponse<NotificationPreferenceDtos.Response>> updatePreference(
            @AuthenticationPrincipal(expression = "id") Long memberId,
            @Valid @RequestBody NotificationPreferenceDtos.UpdateRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "알림 설정이 변경되었습니다.",
                        preferenceService.updatePreference(memberId, request)
                )
        );
    }
}