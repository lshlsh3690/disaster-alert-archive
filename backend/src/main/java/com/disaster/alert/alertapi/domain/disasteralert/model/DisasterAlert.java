package com.disaster.alert.alertapi.domain.disasteralert.model;

import com.disaster.alert.alertapi.domain.region.model.LegalDistrict;
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
    @Column(name = "disaster_alert_id", unique = true)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "disasterAlert", orphanRemoval = true)
    private List<DisasterAlertRegion> regions = new ArrayList<>(); // 재난 알림과 연관된 지역 정보

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
  
    public void setRegions(List<DisasterAlertRegion> regions) {
        this.regions = regions;
    }
}
