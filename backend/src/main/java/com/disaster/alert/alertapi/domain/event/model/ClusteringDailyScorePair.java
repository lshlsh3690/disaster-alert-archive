package com.disaster.alert.alertapi.domain.event.model;

import com.disaster.alert.alertapi.domain.event.judge.JudgeVerdict;
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
 * 일일 채점에서 판사가 판정한 알림 쌍 한 줄(설계: specs/006 plan 5절).
 *
 * <p>불일치 쌍({@link #getDisagreement()} 이 null 이 아닌 것)은 채점 시점의 시스템 이벤트 id 와 함께 남아, SQL 한 번으로
 * 두 알림 원문과 시스템 이벤트를 나란히 볼 수 있다. 이벤트 id 에는 외래 키가 없다 - 재클러스터링으로 이벤트가 지워져도 당시 상태를 남긴다.
 *
 * <p>Red 단계 뼈대 - 값을 담는 구조만 있다.
 */
@Entity
@Table(name = "clustering_daily_score_pair")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClusteringDailyScorePair {

    /** 시스템 배정과 판사 판정이 어긋난 방향. 어긋나지 않은 쌍은 null. */
    public enum Disagreement {
        /** 시스템은 같은 이벤트에 넣었는데 판사는 다른 사건이라고 봄 */
        OVER_MERGED,
        /** 시스템은 다른 이벤트에 넣었는데 판사는 같은 사건이라고 봄 */
        MISSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "score_date", nullable = false)
    private LocalDate scoreDate;

    /** 두 알림 중 id 가 작은 쪽(DB 체크 제약 alert_a &lt; alert_b) */
    @Column(name = "alert_a", nullable = false)
    private long alertA;

    @Column(name = "alert_b", nullable = false)
    private long alertB;

    @Enumerated(EnumType.STRING)
    @Column(name = "judge_verdict", nullable = false, length = 16)
    private JudgeVerdict judgeVerdict;

    @Column(name = "system_same_event", nullable = false)
    private boolean systemSameEvent;

    /** 채점 시점의 alertA 의 이벤트 id, 미배정이면 null */
    @Column(name = "event_a")
    private Long eventA;

    @Column(name = "event_b")
    private Long eventB;

    @Enumerated(EnumType.STRING)
    @Column(name = "disagreement", length = 16)
    private Disagreement disagreement;

    public static ClusteringDailyScorePair of(LocalDate scoreDate, long alertA, long alertB, JudgeVerdict judgeVerdict,
                                              boolean systemSameEvent, Long eventA, Long eventB,
                                              Disagreement disagreement) {
        ClusteringDailyScorePair pair = new ClusteringDailyScorePair();
        pair.scoreDate = scoreDate;
        pair.alertA = alertA;
        pair.alertB = alertB;
        pair.judgeVerdict = judgeVerdict;
        pair.systemSameEvent = systemSameEvent;
        pair.eventA = eventA;
        pair.eventB = eventB;
        pair.disagreement = disagreement;
        return pair;
    }
}
