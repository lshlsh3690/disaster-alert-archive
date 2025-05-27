package com.disaster.alert.alertapi.domain.disasteralert.model;

import com.disaster.alert.alertapi.domain.region.LegalDistrict;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "disaster_alert")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DisasterAlert {
    @Id
    @Column(name = "disaster_alert_id", unique = true)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "alert", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DisasterAlertRegion> alertRegions;

    @Column(name = "sn", unique = true, nullable = false)
    private Long sn;                // SN 외부 OPEN API에서 제공하는 고유번호

    @Column(columnDefinition = "TEXT")
    private String message;           // MSG_CN

    @Column(name = "created_at")
    private LocalDateTime createdAt;  // CRT_DT

    @Column(name = "emergency_level")
    private String emergencyLevel;     // EMRG_STEP_NM

    @Column(name = "disaster_type")
    private String disasterType;      // DST_SE_NM

    @Column(name = "modified_date")
    private LocalDate modifiedDate;   // MDFCN_YMD
}
