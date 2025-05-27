package com.disaster.alert.alertapi.domain.disasteralert.model;


import com.disaster.alert.alertapi.domain.region.LegalDistrict;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "disaster_alert_region")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DisasterAlertRegion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "disaster_alert_region_id", unique = true)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disaster_alert_id")
    private DisasterAlert alert;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_district_code")
    private LegalDistrict region;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

