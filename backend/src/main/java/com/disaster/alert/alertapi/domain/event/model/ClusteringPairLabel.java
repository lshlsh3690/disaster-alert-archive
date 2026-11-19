package com.disaster.alert.alertapi.domain.event.model;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 관리자가 불일치 쌍에 매긴 사람 라벨 한 줄(설계: specs/009-clustering-pair-labeling data-model.md).
 * 쌍마다 한 줄이고 다시 매기면 {@link #relabel} 로 덮어쓴다(처음 매긴 사람·시각은 남는다).
 */
@Entity
@Table(name = "clustering_pair_label")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClusteringPairLabel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 두 알림 중 id 가 작은 쪽(DB 체크 제약 alert_a &lt; alert_b) */
    @Column(name = "alert_a", nullable = false)
    private long alertA;

    @Column(name = "alert_b", nullable = false)
    private long alertB;

    @Enumerated(EnumType.STRING)
    @Column(name = "label", nullable = false, length = 16)
    private Label label;

    @Column(name = "criteria_date", nullable = false)
    private LocalDate criteriaDate;

    @Column(name = "created_by", nullable = false)
    private long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_by", nullable = false)
    private long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 새 라벨은 이 엔티티가 아니라 ClusteringPairLabelRepository.insertIfAbsent(네이티브 INSERT ... ON CONFLICT)로만 만든다.
    // 열을 추가하면 그 SQL 도 함께 고친다 — 만드는 길을 하나로 두려고 엔티티 생성 메서드는 두지 않는다.

    /** 라벨을 고친다. label·criteriaDate·updatedBy·updatedAt 만 바뀌고 created_* 는 그대로다. */
    public void relabel(Label label, LocalDate criteriaDate, long memberId, LocalDateTime now) {
        // created_by/created_at 은 일부러 건드리지 않는다 — "처음 누가 매겼는지"는 덮어써도 남아야 한다(research R3)
        this.label = label;
        this.criteriaDate = criteriaDate;
        this.updatedBy = memberId;
        this.updatedAt = now;
    }
}
