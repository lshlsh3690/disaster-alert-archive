package com.disaster.alert.alertapi.domain.auth.dto;

public record ReissueResponse(String accessToken, String refreshToken) {
    public static ReissueResponse of(String accessToken, String refreshToken) {
        return new ReissueResponse(accessToken, refreshToken);
    }
}