package com.disaster.alert.alertapi.domain.risk.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RegionRiskHistoryId implements Serializable {

    @Column(name = "region_code", length = 20, nullable = false)
    private String regionCode;

    @Column(name = "snapshot_at", nullable = false)
    private LocalDateTime snapshotAt;
}
