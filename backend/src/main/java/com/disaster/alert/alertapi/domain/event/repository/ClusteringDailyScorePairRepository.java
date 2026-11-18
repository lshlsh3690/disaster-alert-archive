package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyScorePair;
import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyScorePair.Disagreement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
