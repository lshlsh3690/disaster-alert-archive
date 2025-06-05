package com.disaster.alert.alertapi.domain.disasteralert.model;

import com.disaster.alert.alertapi.domain.region.model.LegalDistrict;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "disaster_alert_region")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DisasterAlertRegion {

    @EmbeddedId
    private DisasterAlertRegionId id = new DisasterAlertRegionId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("alertId")
    private DisasterAlert disasterAlert;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("districtCode")
    private LegalDistrict legalDistrict;

    public DisasterAlertRegion(DisasterAlert alert, LegalDistrict district) {
        this.disasterAlert = alert;
        this.legalDistrict = district;
    }
}