package com.disaster.alert.alertapi.domain.event.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 이벤트 제목 다국어 번역 캐시 (disaster_alert_translation 패턴).
 * 조회 시 lazy 번역(DeepL) → 저장. (event_id, language_code) 당 1행.
 */
@Entity
@Table(name = "disaster_event_translation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DisasterEventTranslation {

    @EmbeddedId
    private DisasterEventTranslationId id;

    @Column(name = "translated_title", columnDefinition = "TEXT", nullable = false)
    private String translatedTitle;

    @Column(name = "translated_at", nullable = false)
    private LocalDateTime translatedAt;

    public static DisasterEventTranslation of(Long eventId, String languageCode, String translatedTitle) {
        return DisasterEventTranslation.builder()
                .id(new DisasterEventTranslationId(eventId, languageCode))
                .translatedTitle(translatedTitle)
                .translatedAt(LocalDateTime.now())
                .build();
    }
}
