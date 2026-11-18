package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ClusteringDailyScoreRepository extends JpaRepository<ClusteringDailyScore, LocalDate> {

    /** from~to(양 끝 포함) 채점 기록. 기록 없는 날은 결과에 없다 — 빈 날 채우기는 호출한 쪽이 한다. */
    List<ClusteringDailyScore> findByScoreDateBetween(LocalDate from, LocalDate to);

    /**
     * 모든 채점 기록의 (날짜, 판사 버전)만. 판사 버전 이력의 기간 계산용이라 숫자 컬럼까지 읽을 필요가 없다.
     * 날짜당 1행이라 몇 년을 쌓아도 수백 행이다.
     */
    List<DatedVersionView> findAllProjectedBy();

    /** {@link #findAllProjectedBy()} 의 투영. */
    interface DatedVersionView {
        LocalDate getScoreDate();

        String getJudgeVersion();
    }
}
