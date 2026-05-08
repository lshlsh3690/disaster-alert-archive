package com.disaster.alert.alertapi.domain.disasteralert.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Setter
public class DisasterAlertTranslationId implements Serializable {

    @Column(name = "disaster_alert_id")
    private Long alertId;

    @Column(name = "language_code")
    private String languageCode;
}
