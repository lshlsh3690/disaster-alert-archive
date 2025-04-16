package com.disaster.alert.alertapi.domain.disasteralert.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
    private Long id;  // 고유 식별자 (중복 방지)

    @Column(columnDefinition = "TEXT")
    private String message;           // MSG_CN

    private String region;            // RCPTN_RGN_NM

    @Column(name = "created_at")
    private LocalDateTime createdAt;  // CRT_DT

    @Column(name = "emergency_step")
    private String emergencyStep;     // EMRG_STEP_NM

    @Column(name = "disaster_type")
    private String disasterType;      // DST_SE_NM

    @Column(name = "modified_date")
    private LocalDate modifiedDate;   // MDFCN_YMD
}
