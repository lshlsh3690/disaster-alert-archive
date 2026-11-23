package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.model.EvaluationLabelHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationLabelHistoryRepository extends JpaRepository<EvaluationLabelHistory, Long> {
}
