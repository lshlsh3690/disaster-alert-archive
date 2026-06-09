package com.disaster.alert.alertapi.domain.risk.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EventRegionImpactId implements Serializable {

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "region_code", length = 20, nullable = false)
    private String regionCode;
}
