package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.judge.JudgeVerdict;
import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyScorePair;
import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyScorePair.Disagreement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ClusteringDailyScorePairRepository extends JpaRepository<ClusteringDailyScorePair, Long> {

    /** 그날 판정한 쌍 전부. */
    List<ClusteringDailyScorePair> findByScoreDate(LocalDate scoreDate);

    /**
     * from~to(양 끝 포함)의 불일치 쌍 한 페이지. kinds 에 든 종류만 나오므로 맞춘 쌍(disagreement null)은 항상 빠진다.
     * 정렬은 호출한 쪽이 Pageable 로 정한다.
     */
    Page<ClusteringDailyScorePair> findByScoreDateBetweenAndDisagreementIn(LocalDate from, LocalDate to,
                                                                          Collection<Disagreement> kinds,
                                                                          Pageable pageable);

    /**
     * {@link #findByScoreDateBetweenAndDisagreementIn} 와 같은 범위·종류·정렬 규칙에서 사람 라벨이 아직 없는 쌍만.
     * 라벨 테이블에 외래 키가 없어 JOIN 이 아니라 NOT EXISTS 로 (alert_a, alert_b) 가 같은 줄이 없는 쌍을 거른다.
     * 정렬은 Pageable 로 정하고, 총 개수는 같은 조건의 count 를 따로 적어 정렬 절이 섞이지 않게 한다.
     */
    @Query(value = "select p from ClusteringDailyScorePair p where p.scoreDate between :from and :to "
            + "and p.disagreement in :kinds and not exists ("
            + "select 1 from ClusteringPairLabel l where l.alertA = p.alertA and l.alertB = p.alertB)",
            countQuery = "select count(p) from ClusteringDailyScorePair p where p.scoreDate between :from and :to "
                    + "and p.disagreement in :kinds and not exists ("
                    + "select 1 from ClusteringPairLabel l where l.alertA = p.alertA and l.alertB = p.alertB)")
    Page<ClusteringDailyScorePair> findUnlabeledDisagreements(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                                              @Param("kinds") Collection<Disagreement> kinds,
                                                              Pageable pageable);

    /** 이 두 알림이 불일치 쌍으로 한 번이라도 기록됐는가(라벨을 저장할 수 있는 쌍인가). alertA 는 작은 id. */
    boolean existsByAlertAAndAlertBAndDisagreementIsNotNull(long alertA, long alertB);

    /**
     * 지금 불일치로 기록된 채점 쌍 중 사람 라벨이 있는 것 전부(날짜마다 한 줄, 같은 쌍이 여러 날이면 그만큼).
     * 어려운 쌍 성적 집계용이라 판정에 필요한 네 값만 읽는다.
     */
    @Query("select p.scoreDate as scoreDate, l.label as label, p.judgeVerdict as judgeVerdict, "
            + "p.systemSameEvent as systemSameEvent from ClusteringDailyScorePair p, ClusteringPairLabel l "
            + "where p.alertA = l.alertA and p.alertB = l.alertB and p.disagreement is not null")
    List<LabeledDisagreementView> findLabeledDisagreements();

    /** {@link #findLabeledDisagreements()} 의 투영. */
    interface LabeledDisagreementView {
        LocalDate getScoreDate();

        Label getLabel();

        JudgeVerdict getJudgeVerdict();

        boolean getSystemSameEvent();
    }
}
