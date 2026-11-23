package com.disaster.alert.alertapi.domain.event.model;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.global.entity.BaseTimeEntity;
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

/**
 * 클러스터링 평가 셋의 사람 라벨 한 줄(테이블 evaluation_pair_label, V130 + V144). 두 알림이 "같은 사건"인지 사람이 판정한 값이며,
 * 관리자가 화면에서 고친다. stratum·sub_type 같은 옛 열은 관리자 화면에서 쓰지 않아 매핑하지 않는다.
 *
 * <p>라벨 값은 {@link #relabel} 로만 바꾼다. 처음 넣은 시각(created_at)은 그대로 남고, 마지막 수정자·기준 시점이 덮어써진다.
 * 생성·수정 시각(created_at·updated_at)은 {@link BaseTimeEntity} 가 JPA Auditing 으로 채운다 — updated_at 은 라벨이 실제로
 * 바뀌어 UPDATE 가 나갈 때만 바뀌고, 덤프 SQL 로 넣은 행은 고치기 전까지 NULL 이다(= 고친 적 없음).
 * 무엇을 왜 바꿨는지는 이 행이 아니라 {@link EvaluationLabelHistory} 에 쌓인다.
 */
@Entity
@Table(name = "evaluation_pair_label")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvaluationLabel extends BaseTimeEntity {

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

    /** 마지막으로 라벨을 고친 회원 id. 고친 적 없으면 null */
    @Column(name = "updated_by")
    private Long updatedBy;

    /** 라벨을 고친다. label·criteriaDate·updatedBy 가 바뀌고, updated_at 은 UPDATE 때 Auditing 이 채운다. */
    public void relabel(Label newLabel, LocalDate criteriaDate, long memberId) {
        this.label = newLabel;
        this.criteriaDate = criteriaDate;
        this.updatedBy = memberId;
    }
}
