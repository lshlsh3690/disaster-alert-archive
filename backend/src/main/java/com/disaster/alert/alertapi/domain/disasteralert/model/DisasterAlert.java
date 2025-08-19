package com.disaster.alert.alertapi.domain.disasteralert.model;

import com.disaster.alert.alertapi.domain.legaldistrict.model.LegalDistrict;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "disaster_alert")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@ToString
public class DisasterAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "disaster_alert_seq_gen")
    @SequenceGenerator(
            name = "disaster_alert_seq_gen",
            sequenceName = "disaster_alert_seq",
            allocationSize = 500 // 시퀀스 할당 크기
    )
    @Column(name = "disaster_alert_id", unique = true)
    private Long id;

    @Builder.Default
    @OneToMany(mappedBy = "disasterAlert", cascade = CascadeType.PERSIST, orphanRemoval = true)
    private List<DisasterAlertRegion> disasterAlertRegions = new ArrayList<>(); // 법정동 코드

    @Column(name = "sn", unique = true, nullable = false)
    private Long sn;                // SN 외부 OPEN API에서 제공하는 고유번호

    @Column(columnDefinition = "TEXT")
    private String message;           // MSG_CN

    @Column(name = "created_at")
    private LocalDateTime createdAt;  // CRT_DT

    @Enumerated(EnumType.STRING)                // Enum을 문자열로 저장
    @Column(name = "emergency_level")
    private DisasterLevel emergencyLevel;     // EMRG_STEP_NM

    @Column(name = "disaster_type")
    private String disasterType;      // DST_SE_NM

    @Column(name = "modified_date")
    private LocalDateTime modifiedDate;   // MDFCN_YMD

    @Column(name = "original_region", length = 1000)
    private String originalRegion; // 수신지역명 (RCPTN_RGN_NM) - 원본 지역명

    public void addRegion(LegalDistrict region) {
        DisasterAlertRegion link = new DisasterAlertRegion(this, region);
        this.disasterAlertRegions.add(link);
    }

    public void addRegionCode(String districtCode) {
        this.disasterAlertRegions.add(new DisasterAlertRegion(this, districtCode));
    }
}
