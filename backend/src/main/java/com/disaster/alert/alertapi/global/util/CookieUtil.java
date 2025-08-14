package com.disaster.alert.alertapi.global.util;

import lombok.NoArgsConstructor;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

@NoArgsConstructor
public class CookieUtil{
    public static ResponseCookie buildCookie(
            String name,
            String value,
            Duration maxAge,
            boolean secure
    ) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)           // 운영: true
                .path("/")
                .sameSite(secure ? "None" : "Lax") // HTTPS면 None; 개발 HTTP면 Lax
                .maxAge(maxAge)
                .build();
    }
}
