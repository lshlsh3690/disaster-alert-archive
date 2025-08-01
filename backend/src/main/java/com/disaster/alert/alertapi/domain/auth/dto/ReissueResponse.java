package com.disaster.alert.alertapi.domain.auth.dto;

public record ReissueResponse(String accessToken) {
    public static ReissueResponse of(String accessToken) {
        return new ReissueResponse(accessToken);
    }
}