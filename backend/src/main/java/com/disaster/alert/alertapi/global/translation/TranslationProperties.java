package com.disaster.alert.alertapi.global.translation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "deepl")
public class TranslationProperties {
    private String apiKey;
    /**
     * 예비 키. apiKey가 쿼터 초과(456)로 막히면 이 키로 자동 전환된다.
     * DeepL Free는 월간이 아니라 평생 누적 한도라 한 번 소진되면 리셋되지 않으므로,
     * 운영 중 무중단으로 번역을 이어가려면 예비 키가 필요하다. 비어 있으면 폴백 없이
     * apiKey 하나만 사용(설정 안 해도 기존과 동일하게 동작).
     */
    private String apiKey2;
    private String apiUrl;
    private boolean enabled;
}
