package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.model.EvaluationLabel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EvaluationLabelRepository extends JpaRepository<EvaluationLabel, Long> {

    /** 평가 셋 하나의 라벨 목록(페이지). */
    Page<EvaluationLabel> findByDataset(String dataset, Pageable pageable);

    /** 평가 셋 하나에서 라벨 값으로 거른 목록(페이지). */
    Page<EvaluationLabel> findByDatasetAndLabel(String dataset, Label label, Pageable pageable);

    /** 평가 셋의 라벨별 개수(목록 위 요약용). */
    long countByDatasetAndLabel(String dataset, Label label);

    /**
     * 수정용 조회 — 그 행을 잠그고(SELECT ... FOR UPDATE) 읽는다. 두 관리자가 같은 라벨을 거의 동시에 고쳐도 한쪽이 끝날 때까지
     * 기다리게 해, 이력의 "이전 라벨"이 실제 직전 값과 어긋나지 않게 한다(둘 다 같은 옛 값을 읽고 이력을 남기는 일을 막는다).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EvaluationLabel> findForUpdateById(Long id);

    /** 셋 목록 요약 한 줄. 네이티브 쿼리 결과를 받는 모양이다. */
    interface DatasetSummary {
        String getDataset();

        long getPairs();

        long getSame();

        long getDiff();

        long getUnsure();

        LocalDateTime getLastUpdatedAt();
    }

    // 셋마다 쌍 수·라벨별 개수·마지막 수정 시각을 한 번에 센다. 라벨별 개수를 셋마다 세 번씩 따로 세지 않으려고 FILTER 를 쓴다(Postgres)
    @Query(value = """
            SELECT dataset AS dataset,
                   count(*) AS pairs,
                   count(*) FILTER (WHERE label = 'SAME') AS same,
                   count(*) FILTER (WHERE label = 'DIFF') AS diff,
                   count(*) FILTER (WHERE label = 'UNSURE') AS unsure,
                   max(updated_at) AS lastUpdatedAt
            FROM evaluation_pair_label
            GROUP BY dataset
            ORDER BY dataset
            """, nativeQuery = true)
    List<DatasetSummary> summarizeDatasets();

    /** 평가 도구용: 한 셋의 라벨 전부를 넣은 순서(id)로. 순서가 같아야 두 리포트의 쌍 목록을 비교할 수 있다. */
    List<EvaluationLabel> findByDatasetOrderByIdAsc(String dataset);
}
