package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.model.EvaluationLabel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvaluationLabelRepository extends JpaRepository<EvaluationLabel, Long> {

    /** 평가 셋 하나의 라벨 목록(페이지). */
    Page<EvaluationLabel> findByDataset(String dataset, Pageable pageable);

    /** 평가 셋 하나에서 라벨 값으로 거른 목록(페이지). */
    Page<EvaluationLabel> findByDatasetAndLabel(String dataset, Label label, Pageable pageable);

    /** 평가 셋의 라벨별 개수(목록 위 요약용). */
    long countByDatasetAndLabel(String dataset, Label label);

    /** 셋 이름 없이 라벨 값으로만 거른 목록(셋 이름을 주지 않았을 때). */
    Page<EvaluationLabel> findByLabel(Label label, Pageable pageable);

    /** 셋 이름 없이 센 라벨별 개수. */
    long countByLabel(Label label);

    /** 평가 도구용: 한 셋의 라벨 전부를 문항 번호 순으로. 순서가 같아야 두 리포트의 쌍 목록을 비교할 수 있다. */
    List<EvaluationLabel> findByDatasetOrderByQAscIdAsc(String dataset);
}
