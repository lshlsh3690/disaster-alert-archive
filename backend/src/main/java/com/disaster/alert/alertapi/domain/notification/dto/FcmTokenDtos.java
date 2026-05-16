package com.disaster.alert.alertapi.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;

public class FcmTokenDtos {

    public record RegisterRequest(
            @NotBlank(message = "FCM 토큰은 필수입니다.")
            String token,

            @NotBlank(message = "deviceType은 필수입니다.")
            String deviceType  // WEB / ANDROID / IOS
    ) {}
}