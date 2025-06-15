package com.disaster.alert.alertapi.domain.disasteralert.model;

import com.disaster.alert.alertapi.domain.legaldistrict.model.LegalDistrict;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @Setter
    @JoinColumn(name = "legal_district_code", nullable = false)
    private LegalDistrict legalDistrict;

    @Column(name = "sn", nullable = false)
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
}
