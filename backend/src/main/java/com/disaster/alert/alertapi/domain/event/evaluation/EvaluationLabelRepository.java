package com.disaster.alert.alertapi.domain.event.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EvaluationLabelRepository extends JpaRepository<EvaluationLabel, Long> {

    List<EvaluationLabel> findByDatasetOrderById(String dataset);

    long countByDataset(String dataset);

    /** 데이터셋 단위 일괄 삭제(벌크 쿼리). 삭제된 행 수를 반환한다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from EvaluationLabel l where l.dataset = :dataset")
    int deleteByDataset(@Param("dataset") String dataset);
}
