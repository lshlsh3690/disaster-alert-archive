package com.disaster.alert.alertapi.domain.event.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * disaster_event_translation 복합 PK — (이벤트 id, 언어코드).
 * languageCode 는 대문자(EN/JA/ZH) — {@code SupportedLanguage.getDbCode()}.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DisasterEventTranslationId implements Serializable {

    @Column(name = "disaster_event_id")
    private Long eventId;

    @Column(name = "language_code")
    private String languageCode;
}
