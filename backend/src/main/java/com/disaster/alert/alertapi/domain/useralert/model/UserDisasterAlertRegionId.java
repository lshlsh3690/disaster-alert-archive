package com.disaster.alert.alertapi.domain.useralert.model;

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
public class UserDisasterAlertRegionId implements Serializable {
    @Column(name = "user_disaster_alert_id")
    private Long userDisasterAlertId;

    @Column(name = "legal_district_code", length = 10)
    private String legalDistrictCode;
}