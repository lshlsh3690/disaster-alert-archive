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
 * 클러스터링 평가 셋의 사람 라벨 한 줄(테이블 evaluation_label, V130 + V144). 두 알림이 "같은 사건"인지 사람이 판정한 값이며,
 * 관리자가 화면에서 고친다. stratum·sub_type 같은 옛 열은 관리자 화면에서 쓰지 않아 매핑하지 않는다.
 *
 * <p>Red 단계 뼈대 — 라벨을 고치는 {@link #relabel} 은 아무것도 바꾸지 않는다.
 */
// Red 단계 뼈대 — 구현은 Green 에서
@Entity
@Table(name = "evaluation_label")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvaluationLabel {

    /** 두 알림의 지역 관계(JSON 의 relation). 옛 행에는 없어 null 일 수 있다. */
    public enum Relation { SAME_SIGUNGU, ADJACENT_SIGUNGU }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 평가 셋 이름(예: cluster-eval-2026-11) */
    @Column(name = "dataset", nullable = false, length = 50)
    private String dataset;

    /** 평가 셋 문항 번호 */
    @Column(name = "q")
    private Integer q;

    @Column(name = "alert_a_id", nullable = false)
    private long alertAId;

    @Column(name = "alert_b_id", nullable = false)
    private long alertBId;

    @Enumerated(EnumType.STRING)
    @Column(name = "label", nullable = false, length = 10)
    private Label label;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation", length = 16)
    private Relation relation;

    /** 라벨을 매긴(마지막으로 고친) 때의 판정 기준 시점 */
    @Column(name = "criteria_date")
    private LocalDate criteriaDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 마지막으로 라벨을 고친 회원 id. 고친 적 없으면 null */
    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 라벨을 고친다. label·criteriaDate·updatedBy·updatedAt 이 바뀐다. */
    public void relabel(Label newLabel, LocalDate criteriaDate, long memberId, LocalDateTime now) {
        // Red 단계 뼈대 — 구현은 Green 에서
    }
}
