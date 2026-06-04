package com.disaster.alert.alertapi.domain.event.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EventAlertMappingId implements Serializable {

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "alert_id", nullable = false)
    private Long alertId;
}
