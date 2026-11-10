package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface ClusteringDailyCheckRepository extends JpaRepository<ClusteringDailyCheck, LocalDate> {
}
