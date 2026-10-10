package com.disaster.alert.alertapi.domain.event.model;

import java.time.LocalDateTime;

// Red 단계 뼈대 — 구현은 Green 에서
public final class EmbeddingInputBuilder {

    private EmbeddingInputBuilder() {
    }

    public record Options(boolean includeTypeAndRegion, boolean includeTimeBucket) {
    }

    public static String build(String disasterType, String sigunguName, LocalDateTime sentAt,
                               String message, Options options) {
        return "";
    }
}
