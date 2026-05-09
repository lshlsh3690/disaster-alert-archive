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
            boolean secure,
            String domain     

    ) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(secure)
            .path("/")
            .sameSite(secure ? "None" : "Lax")
            .maxAge(maxAge);

        if (domain != null && !domain.isBlank()) {
            builder.domain(domain);  // .disaster-alert-archive.co.kr
        }

        return builder.build();
    }
}
