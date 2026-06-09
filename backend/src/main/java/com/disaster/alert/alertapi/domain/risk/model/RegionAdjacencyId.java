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
public class RegionAdjacencyId implements Serializable {

    @Column(name = "region_code", length = 5, nullable = false)
    private String regionCode;

    @Column(name = "neighbor_code", length = 5, nullable = false)
    private String neighborCode;
}
