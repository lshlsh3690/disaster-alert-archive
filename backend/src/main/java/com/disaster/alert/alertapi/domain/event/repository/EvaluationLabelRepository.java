package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.model.EvaluationLabel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationLabelRepository extends JpaRepository<EvaluationLabel, Long> {

    /** 평가 셋 하나의 라벨 목록(페이지). */
    Page<EvaluationLabel> findByDataset(String dataset, Pageable pageable);

    /** 평가 셋 하나에서 라벨 값으로 거른 목록(페이지). */
    Page<EvaluationLabel> findByDatasetAndLabel(String dataset, Label label, Pageable pageable);

    /** 평가 셋의 라벨별 개수(목록 위 요약용). */
    long countByDatasetAndLabel(String dataset, Label label);
}
