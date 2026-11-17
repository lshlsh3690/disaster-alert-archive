package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyScorePair;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ClusteringDailyScorePairRepository extends JpaRepository<ClusteringDailyScorePair, Long> {

    /** 그날 판정한 쌍 전부. */
    List<ClusteringDailyScorePair> findByScoreDate(LocalDate scoreDate);
}
