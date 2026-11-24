package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.model.EvaluationLabelHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvaluationLabelHistoryRepository extends JpaRepository<EvaluationLabelHistory, Long> {

    /** 라벨 하나의 이력, 최신 먼저(같은 시각이면 나중에 들어간 것 먼저). */
    List<EvaluationLabelHistory> findByLabelIdOrderByChangedAtDescIdDesc(long labelId);
}
