package com.disaster.alert.alertapi.domain.disasteralert.model;

import com.disaster.alert.alertapi.domain.disasteralert.dto.DisasterAlertResponseDto;
import com.disaster.alert.alertapi.domain.legaldistrict.model.LegalDistrict;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Cascade;

@Entity
@Table(name = "disaster_alert_region")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DisasterAlertRegion {
    @EmbeddedId
    private DisasterAlertRegionId id = new DisasterAlertRegionId();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disaster_alert_id", insertable = false, updatable = false)
    private DisasterAlert disasterAlert;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_district_code", insertable = false, updatable = false)
    private LegalDistrict legalDistrict;

    @PrePersist
    void prePersist() {
        if (this.id.getAlertId() == null && this.disasterAlert != null) {
            this.id.setAlertId(this.disasterAlert.getId());
        }
    }

    public DisasterAlertRegion(DisasterAlert alert, LegalDistrict district) {
        this.disasterAlert = alert;
        this.legalDistrict = district;
    }

    public DisasterAlertRegion(DisasterAlert alert, String code){
        this.disasterAlert = alert;
        this.id.setDistrictCode(code);
    }
}