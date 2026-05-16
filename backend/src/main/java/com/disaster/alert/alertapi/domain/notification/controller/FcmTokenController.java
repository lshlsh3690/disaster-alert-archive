package com.disaster.alert.alertapi.domain.notification.controller;

import com.disaster.alert.alertapi.domain.notification.dto.FcmTokenDtos;
import com.disaster.alert.alertapi.domain.notification.service.FcmTokenService;
import com.disaster.alert.alertapi.global.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/fcm-token")
public class FcmTokenController {

    private final FcmTokenService fcmTokenService;

    // FCM 토큰 등록/갱신
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> registerToken(
            @AuthenticationPrincipal(expression = "id") Long memberId,
            @Valid @RequestBody FcmTokenDtos.RegisterRequest request
    ) {
        fcmTokenService.registerToken(memberId, request);
        return ResponseEntity.ok(ApiResponse.success("FCM 토큰이 등록되었습니다.", null));
    }

    // FCM 토큰 삭제 (로그아웃 시 호출)
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteToken(
            @RequestParam String token
    ) {
        fcmTokenService.deleteToken(token);
        return ResponseEntity.ok(ApiResponse.success("FCM 토큰이 삭제되었습니다.", null));
    }
}