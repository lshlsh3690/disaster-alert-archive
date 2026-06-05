package com.disaster.alert.alertapi.domain.event.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 이벤트 ↔ 재난문자 N:M 매핑.
 *
 * <p>한 alert 는 하나의 event 에만 속한다 (DB CONSTRAINT uq_event_alert_mapping_alert).
 * sequence_no 는 이벤트 내 순서 (1부터 1씩 증가) — 타임라인 UI 에서 시간순 + 순번으로 표시.
 */
@Entity
@Table(name = "event_alert_mapping")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class EventAlertMapping {

    @EmbeddedId
    private EventAlertMappingId id;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @CreatedDate
    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    /**
     * 합류 시점 코사인 유사도 (같은 이벤트의 최근접 멤버 알림 대비, 1 - min distance).
     * 개발자용 튜닝/감사 신호 — 사용자 비노출. NULL = 비교 없이 들어온 행:
     * 이벤트 첫 알림(seq=1), broadcast 머지(임베딩 미사용), LLM 머지.
     */
    @Column(name = "similarity_score")
    private Double similarityScore;

    /** 합류 방식 표식 (SEED/EMBEDDING/BROADCAST/LLM). 감사·튜닝용, 특히 LLM 머지 검수. */
    @Enumerated(EnumType.STRING)
    @Column(name = "merge_method", length = 16)
    private MergeMethod mergeMethod;

    public static EventAlertMapping of(Long eventId, Long alertId, int sequenceNo,
                                       Double similarityScore, MergeMethod mergeMethod) {
        return new EventAlertMapping(
                new EventAlertMappingId(eventId, alertId), sequenceNo, null, similarityScore, mergeMethod);
    }
}
