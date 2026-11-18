package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ClusteringDailyCheckRepository extends JpaRepository<ClusteringDailyCheck, LocalDate> {

    /** from~to(양 끝 포함) 구조 검사 기록. 기록 없는 날은 결과에 없다. */
    List<ClusteringDailyCheck> findByCheckDateBetween(LocalDate from, LocalDate to);
}
