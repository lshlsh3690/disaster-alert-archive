package com.disaster.alert.alertapi.domain.event.model;

import com.disaster.alert.alertapi.domain.event.judge.RecordedVerdict;
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

/**
 * 측정 하나({@link JudgeAgreementRun})에서 알림 쌍 하나에 판사가 낸 판정(V145). 사람 라벨은 담지 않는다 —
 * 일치율은 현재 라벨로 센다. 저장 뒤 고치지 않는다.
 *
 * <p>측정은 run_id 로 묶어 읽으므로 객체 연관 없이 id 만 둔다. 알림도 id 로만 가리킨다(외래 키 없음).
 */
@Entity
@Table(name = "judge_agreement_run_pair")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JudgeAgreementRunPair {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private long runId;

    /** 두 알림 중 id 가 작은 쪽(DB 체크 제약 alert_a &lt; alert_b) */
    @Column(name = "alert_a", nullable = false)
    private long alertA;

    @Column(name = "alert_b", nullable = false)
    private long alertB;

    @Enumerated(EnumType.STRING)
    @Column(name = "verdict", nullable = false, length = 16)
    private RecordedVerdict verdict;

    /** 쌍 하나의 판정. 알림 순서는 받은 대로 두지 않고 작은 id 를 앞에 둔다(라벨 쪽 순서와 상관없이 맞추려고). */
    public static JudgeAgreementRunPair of(long runId, long alertA, long alertB, RecordedVerdict verdict) {
        if (alertA == alertB) {
            throw new IllegalArgumentException("같은 알림끼리의 쌍입니다: " + alertA);
        }
        JudgeAgreementRunPair pair = new JudgeAgreementRunPair();
        pair.runId = runId;
        pair.alertA = Math.min(alertA, alertB);
        pair.alertB = Math.max(alertA, alertB);
        pair.verdict = verdict;
        return pair;
    }
}
