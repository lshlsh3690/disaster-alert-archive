package com.disaster.alert.alertapi.domain.weather.repository;

import com.disaster.alert.alertapi.domain.weather.model.BackfillStatus;
import com.disaster.alert.alertapi.domain.weather.model.WeatherBackfillProgress;
import com.disaster.alert.alertapi.domain.weather.model.WeatherBackfillProgressId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * 백필 진행 상태 레포지토리.
 */
public interface WeatherBackfillProgressRepository
        extends JpaRepository<WeatherBackfillProgress, WeatherBackfillProgressId> {

    /** 동시 실행 방지 — 진행 중 셀이 하나라도 있으면 백필러 시작 거부. */
    boolean existsByStatus(BackfillStatus status);

    long countByStatus(BackfillStatus status);

    /** /status 응답에서 실패 사유 샘플 노출. */
    List<WeatherBackfillProgress> findTop20ByStatusOrderByFinishedAtDesc(BackfillStatus status);

    /** 적재 row 총합 (DONE 만). */
    @Query("SELECT COALESCE(SUM(p.rowCount), 0) FROM WeatherBackfillProgress p WHERE p.status = 'DONE'")
    long sumRowCountOfDone();
}
