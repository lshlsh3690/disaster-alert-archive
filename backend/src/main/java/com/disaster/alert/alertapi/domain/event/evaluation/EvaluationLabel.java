package com.disaster.alert.alertapi.domain.event.evaluation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/** 클러스터링 평가용 정답 라벨 한 쌍 (테이블 evaluation_label, V130). 운영 코드 경로에서는 쓰지 않는다. */
@Entity
@Table(name = "evaluation_label")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EvaluationLabel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset", nullable = false, length = 50)
    private String dataset;

    @Column(name = "alert_a_id", nullable = false)
    private Long alertAId;

    @Column(name = "alert_b_id", nullable = false)
    private Long alertBId;

    @Enumerated(EnumType.STRING)
    @Column(name = "label", nullable = false, length = 10)
    private EvaluationLabelType label;

    @Enumerated(EnumType.STRING)
    @Column(name = "stratum", nullable = false, length = 10)
    private EvaluationStratum stratum;

    @Column(name = "sub_type", nullable = false, length = 40)
    private String subType;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static EvaluationLabel of(String dataset, EvaluationLabelRecord record) {
        EvaluationLabel entity = new EvaluationLabel();
        entity.dataset = dataset;
        entity.alertAId = record.alertAId();
        entity.alertBId = record.alertBId();
        entity.label = record.label();
        entity.stratum = record.stratum();
        entity.subType = record.subType();
        return entity;
    }
}
