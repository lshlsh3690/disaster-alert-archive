package com.disaster.alert.alertapi.domain.event.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 클러스터링 일일 채점 결과 - 날짜당 1줄(설계: specs/006 plan 4·5절).
 *
 * <p>그날 사건 알림을 판사(LLM)가 본 판정으로 시스템 묶음을 채점한 숫자다. 비율·점수는 저장하지 않고 이 숫자에서 계산한다
 * (분모를 잃지 않기 위해). 판사 버전과 그 버전의 272쌍 일치율은 그날 값을 복사해 둔다 - 상수가 바뀌어도 그날의 점수를
 * 어떤 신뢰도로 읽어야 하는지 남는다.
 *
 * <p>Red 단계 뼈대 - 값을 담는 구조만 있고, 채우는 로직(채점)은 {@code ClusteringDailyScoreService} 의 Green 에서 쓴다.
 */
@Entity
@Table(name = "clustering_daily_score")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClusteringDailyScore {

    @Id
    @Column(name = "score_date")
    private LocalDate scoreDate;

    @Column(name = "incident_count", nullable = false)
    private int incidentCount;

    @Column(name = "pair_count", nullable = false)
    private int pairCount;

    @Column(name = "correctly_merged", nullable = false)
    private int correctlyMerged;

    @Column(name = "over_merged", nullable = false)
    private int overMerged;

    @Column(name = "missed", nullable = false)
    private int missed;

    @Column(name = "unsure_count", nullable = false)
    private int unsureCount;

    @Column(name = "format_failure_count", nullable = false)
    private int formatFailureCount;

    @Column(name = "judge_version", nullable = false, length = 16)
    private String judgeVersion;

    @Column(name = "agreement_same_num", nullable = false)
    private int agreementSameNum;

    @Column(name = "agreement_same_den", nullable = false)
    private int agreementSameDen;

    @Column(name = "agreement_diff_num", nullable = false)
    private int agreementDiffNum;

    @Column(name = "agreement_diff_den", nullable = false)
    private int agreementDiffDen;

    @Column(name = "prompt_tokens", nullable = false)
    private long promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private long completionTokens;

    @Column(name = "scored_at", nullable = false)
    private LocalDateTime scoredAt;

    /** 그날 채점 결과 한 줄을 만든다. 같은 날을 다시 채점하지 않으므로 갱신 메서드는 두지 않는다. */
    public static ClusteringDailyScore of(LocalDate scoreDate, int incidentCount, int pairCount,
                                          int correctlyMerged, int overMerged, int missed,
                                          int unsureCount, int formatFailureCount,
                                          String judgeVersion, int agreementSameNum, int agreementSameDen,
                                          int agreementDiffNum, int agreementDiffDen,
                                          long promptTokens, long completionTokens, LocalDateTime scoredAt) {
        ClusteringDailyScore score = new ClusteringDailyScore();
        score.scoreDate = scoreDate;
        score.incidentCount = incidentCount;
        score.pairCount = pairCount;
        score.correctlyMerged = correctlyMerged;
        score.overMerged = overMerged;
        score.missed = missed;
        score.unsureCount = unsureCount;
        score.formatFailureCount = formatFailureCount;
        score.judgeVersion = judgeVersion;
        score.agreementSameNum = agreementSameNum;
        score.agreementSameDen = agreementSameDen;
        score.agreementDiffNum = agreementDiffNum;
        score.agreementDiffDen = agreementDiffDen;
        score.promptTokens = promptTokens;
        score.completionTokens = completionTokens;
        score.scoredAt = scoredAt;
        return score;
    }
}
