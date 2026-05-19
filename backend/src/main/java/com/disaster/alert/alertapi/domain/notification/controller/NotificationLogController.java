package com.disaster.alert.alertapi.domain.notification.controller;

import com.disaster.alert.alertapi.domain.notification.dto.NotificationLogDtos;
import com.disaster.alert.alertapi.domain.notification.service.NotificationLogService;
import com.disaster.alert.alertapi.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notification-logs")
public class NotificationLogController {

    private final NotificationLogService notificationLogService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<ApiResponse<NotificationLogDtos.PageResponse>> getMyNotifications(
            @AuthenticationPrincipal(expression = "id") Long memberId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(notificationLogService.getMyNotifications(memberId, pageable)));
    }

    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal(expression = "id") Long memberId
    ) {
        notificationLogService.markAsRead(id, memberId);
        return ResponseEntity.ok(ApiResponse.empty());
    }
}
