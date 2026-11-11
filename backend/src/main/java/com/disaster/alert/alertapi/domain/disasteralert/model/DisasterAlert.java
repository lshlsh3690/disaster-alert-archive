package com.disaster.alert.alertapi.domain.disasteralert.model;

import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import com.disaster.alert.alertapi.domain.event.model.MergeMethod;
import com.disaster.alert.alertapi.domain.legaldistrict.model.LegalDistrict;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@DynamicUpdate   // 바뀐 컬럼만 UPDATE — 이벤트 배정이 다른 작업이 채운 컬럼(alert_kind 등)을 옛 값으로 덮지 않게
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

    @Enumerated(EnumType.STRING)              // Enum을 문자열로 저장
    @Column(name = "emergency_level")
    private DisasterLevel emergencyLevel;     // EMRG_STEP_NM

    @Column(name = "disaster_type")
    private String disasterType;      // DST_SE_NM

    @Column(name = "modified_date")
    private LocalDateTime modifiedDate;   // MDFCN_YMD

    @Column(name = "original_region", columnDefinition = "TEXT")
    private String originalRegion; // 수신지역명 (RCPTN_RGN_NM) - 원본 지역명. 읍면동이 많은 광역
    // 경보는 쉽게 1000자를 넘겨 VARCHAR(1000)에서 INSERT 실패가 났었다(V131) — 조회/조인에 쓰이지
    // 않는 참고용 원본 텍스트라 길이 제한 둘 이유가 없어 TEXT로 둔다.

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_kind", length = 16)
    private AlertKind alertKind;    // LLM 분류 결과(사건/비사건), 미분류면 null

    /** 소속 이벤트(이벤트 1 : 알림 N). 아직 클러스터링되지 않았거나 비사건이면 null. */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private DisasterEvent event;

    /** 이벤트 안 순번(1부터). 타임라인 순번 표시·대표 알림(1번) 조회에 쓴다. */
    @Column(name = "event_sequence_no")
    private Integer eventSequenceNo;

    /** 합류 시점 코사인 유사도(1 - 최소 거리). 비교 없이 들어오면 null(첫 알림·광역·키·LLM 병합). 튜닝·감사용. */
    @Column(name = "event_similarity_score")
    private Double eventSimilarityScore;

    /** 이벤트에 합류한 방식. 감사·튜닝용, 특히 LLM 병합 검수. */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_merge_method", length = 16)
    private MergeMethod eventMergeMethod;

    /** 이벤트에 합류한 시각. */
    @Column(name = "event_added_at")
    private LocalDateTime eventAddedAt;

    public void classifyAs(AlertKind kind) {
        this.alertKind = kind;
    }

    /** 이벤트에 넣는다. 첫 알림이면 sequenceNo=1, mergeMethod=SEED. */
    public void assignToEvent(DisasterEvent event, int sequenceNo, Double similarityScore, MergeMethod mergeMethod) {
        this.event = event;
        this.eventSequenceNo = sequenceNo;
        this.eventSimilarityScore = similarityScore;
        this.eventMergeMethod = mergeMethod;
        this.eventAddedAt = LocalDateTime.now();
    }

    /** 소속 이벤트 id(없으면 null). 지연 로딩 프록시라도 id 는 조회 없이 읽힌다. */
    public Long getEventId() {
        return event == null ? null : event.getId();
    }

    public void addRegion(LegalDistrict region) {
        DisasterAlertRegion link = new DisasterAlertRegion(this, region);
        this.disasterAlertRegions.add(link);
    }

    public void addRegionCode(String districtCode) {
        this.disasterAlertRegions.add(new DisasterAlertRegion(this, districtCode));
    }
}
