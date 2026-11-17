package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface ClusteringDailyScoreRepository extends JpaRepository<ClusteringDailyScore, LocalDate> {
}
