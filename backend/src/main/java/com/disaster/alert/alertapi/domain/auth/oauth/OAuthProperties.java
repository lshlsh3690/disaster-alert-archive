package com.disaster.alert.alertapi.domain.auth.oauth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "oauth")
public class OAuthProperties {
    private Provider google;
    private Provider kakao;
    private Provider naver;
    private String successRedirect; // e.g. https://app.example.com/dashboard

    @Getter
    @Setter
    public static class Provider {
        private String clientId;
        private String clientSecret;
        private String redirectUri; // backend callback uri
    }
}


