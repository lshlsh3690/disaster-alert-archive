package com.disaster.alert.alertapi.domain.event.model;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 판사 버전 하나를 평가 셋 하나로 잰 일치율 측정(V145, specs/014). 저장 뒤 고치지 않는다 — 수정 메서드가 없고,
 * 다시 재면 새 행을 만든다.
 *
 * <p>일치율은 저장하지 않는다. 쌍별 판정({@link JudgeAgreementRunPair})과 현재 사람 라벨로 그때그때 센다.
 * 쌍별 판정이 없는 옛 측정(v1)만 합계({@code legacy*})를 들고 있다.
 */
@Entity
@Table(name = "judge_agreement_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JudgeAgreementRun {

    /** 측정을 만든 경로. 시드로 옮긴 옛 측정과 평가 도구로 잰 측정을 구분한다. */
    public enum Origin { LEGACY_IMPORT, CLI }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "judge_version", nullable = false, length = 16)
    private String judgeVersion;

    @Column(name = "model", nullable = false, length = 32)
    private String model;

    @Column(name = "dataset", nullable = false, length = 50)
    private String dataset;

    @Column(name = "origin", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private Origin origin;

    @Column(name = "finished_at", nullable = false)
    private LocalDateTime finishedAt;

    @Column(name = "prompt_tokens", nullable = false)
    private long promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private long completionTokens;

    @Column(name = "estimated_usd", nullable = false, precision = 10, scale = 4)
    private BigDecimal estimatedUsd;

    @Column(name = "has_pair_verdicts", nullable = false)
    private boolean hasPairVerdicts;

    @Column(name = "legacy_same_num")
    private Integer legacySameNum;

    @Column(name = "legacy_same_den")
    private Integer legacySameDen;

    @Column(name = "legacy_diff_num")
    private Integer legacyDiffNum;

    @Column(name = "legacy_diff_den")
    private Integer legacyDiffDen;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 평가 도구로 잰 측정. 쌍별 판정은 {@link JudgeAgreementRunPair} 로 따로 저장한다. */
    public static JudgeAgreementRun measured(String judgeVersion, String model, String dataset, LocalDateTime finishedAt,
                                             long promptTokens, long completionTokens, BigDecimal estimatedUsd) {
        JudgeAgreementRun run = new JudgeAgreementRun();
        run.judgeVersion = judgeVersion;
        run.model = model;
        run.dataset = dataset;
        run.origin = Origin.CLI;
        run.finishedAt = finishedAt;
        run.promptTokens = promptTokens;
        run.completionTokens = completionTokens;
        run.estimatedUsd = estimatedUsd;
        run.hasPairVerdicts = true;
        return run;
    }
}
