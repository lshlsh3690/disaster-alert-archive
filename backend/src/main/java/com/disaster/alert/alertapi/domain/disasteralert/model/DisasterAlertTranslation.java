package com.disaster.alert.alertapi.domain.disasteralert.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "disaster_alert_translation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DisasterAlertTranslation {

    @EmbeddedId
    private DisasterAlertTranslationId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disaster_alert_id", insertable = false, updatable = false)
    private DisasterAlert disasterAlert;

    @Column(name = "translated_message", columnDefinition = "TEXT", nullable = false)
    private String translatedMessage;

    @Column(name = "translated_disaster_type")
    private String translatedDisasterType;

    @Column(name = "translated_region_names", columnDefinition = "TEXT")
    private String translatedRegionNames;

    @Column(name = "translated_at", nullable = false)
    private LocalDateTime translatedAt;

    public static DisasterAlertTranslation of(Long alertId, String languageCode, String translatedMessage,
                                              String translatedDisasterType, String translatedRegionNames) {
        return DisasterAlertTranslation.builder()
                .id(new DisasterAlertTranslationId(alertId, languageCode))
                .translatedMessage(translatedMessage)
                .translatedDisasterType(translatedDisasterType)
                .translatedRegionNames(translatedRegionNames)
                .translatedAt(LocalDateTime.now())
                .build();
    }
}
