package com.disaster.alert.alertapi.domain.useralert.model;

import com.disaster.alert.alertapi.domain.legaldistrict.model.LegalDistrict;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_disaster_alert_region")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UserDisasterAlertRegion {

    @EmbeddedId
    private UserDisasterAlertRegionId id;

    @MapsId("userDisasterAlertId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_disaster_alert_id", insertable = false, updatable = false)
    private UserDisasterAlert userDisasterAlert;

    @MapsId("legalDistrictCode")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_district_code", insertable = false, updatable = false)
    private LegalDistrict legalDistrict;

    // 새로 만들 때 편의 생성자
    public UserDisasterAlertRegion(UserDisasterAlert alert, LegalDistrict legalDistrict) {
        this.userDisasterAlert = alert;
        this.legalDistrict = legalDistrict;
        this.id = new UserDisasterAlertRegionId(alert.getId(), legalDistrict.getCode());
    }

    public UserDisasterAlertRegion(UserDisasterAlert alert, String legalDistrictCode) {
        this.userDisasterAlert = alert;
        this.legalDistrict = LegalDistrict.referencedByCode(legalDistrictCode); // getReferenceById()와 같은 패턴
        this.id = new UserDisasterAlertRegionId(alert.getId(), legalDistrictCode);
    }
}


