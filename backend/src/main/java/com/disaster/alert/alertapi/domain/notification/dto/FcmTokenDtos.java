package com.disaster.alert.alertapi.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class FcmTokenDtos {

    public record RegisterRequest(
            @NotBlank(message = "FCM 토큰은 필수입니다.")
            String token,

            @NotBlank(message = "deviceType은 필수입니다.")
            String deviceType  // WEB / ANDROID / IOS
    ) {}

    public record GuestRegisterRequest(
            @NotBlank(message = "FCM 토큰은 필수입니다.")
            String token,

            @NotBlank(message = "deviceType은 필수입니다.")
            String deviceType,

            @NotEmpty(message = "관심지역 코드는 최소 1개 이상이어야 합니다.")
            List<@NotBlank String> legalDistrictCodes
    ) {}

    public record GuestLinkRequest(
            @NotBlank(message = "FCM 토큰은 필수입니다.")
            String token
    ) {}
}