package com.disaster.alert.alertapi.domain.event.model;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
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

/**
 * 평가 셋 라벨을 바꾼 기록 한 줄(테이블 evaluation_pair_label_history, V144). 바꿀 때마다 이전·새 라벨, 사유, 수정자, 시각을 남긴다.
 * 평가의 정답이 바뀐 이유를 나중에 추적하려는 용도라 고치거나 지우지 않는다(라벨 행이 지워질 때만 함께 지워진다).
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "evaluation_pair_label_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvaluationLabelHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "label_id", nullable = false)
    private long labelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_label", nullable = false, length = 10)
    private Label oldLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_label", nullable = false, length = 10)
    private Label newLabel;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "changed_by", nullable = false)
    private long changedBy;

    /** 바꾼 시각 — 저장할 때 Auditing 이 채운다 */
    @CreatedDate
    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    public static EvaluationLabelHistory of(long labelId, Label oldLabel, Label newLabel, String reason,
                                            long changedBy) {
        EvaluationLabelHistory history = new EvaluationLabelHistory();
        history.labelId = labelId;
        history.oldLabel = oldLabel;
        history.newLabel = newLabel;
        history.reason = reason;
        history.changedBy = changedBy;
        return history;
    }
}
