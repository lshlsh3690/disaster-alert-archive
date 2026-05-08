package com.disaster.alert.alertapi.global.translation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "deepl")
public class TranslationProperties {
    private String apiKey;
    private String apiUrl;
    private boolean enabled;
}
