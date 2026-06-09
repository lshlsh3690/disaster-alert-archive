package com.disaster.alert.alertapi.domain.risk.repository;

import com.disaster.alert.alertapi.domain.risk.model.RegionRiskDaily;
import com.disaster.alert.alertapi.domain.risk.model.RegionRiskDailyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RegionRiskDailyRepository extends JpaRepository<RegionRiskDaily, RegionRiskDailyId> {

    @Query("""
            SELECT d FROM RegionRiskDaily d
            WHERE d.id.snapshotDate >= :start
              AND d.id.snapshotDate <= :end
            ORDER BY d.id.snapshotDate ASC
            """)
    List<RegionRiskDaily> findMapSeriesDaily(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Modifying
    @Query(value = """
            INSERT INTO region_risk_daily (region_code, snapshot_date, risk_score)
            SELECT DISTINCT ON (region_code)
                   region_code, 
                   CAST(snapshot_at AS DATE) AS snapshot_date,
                   risk_score
            FROM region_risk_history
            WHERE snapshot_at >= :startOfDay AND snapshot_at < :endOfDay
            ORDER BY region_code, risk_score DESC
            ON CONFLICT (region_code, snapshot_date) DO UPDATE SET risk_score = EXCLUDED.risk_score
            """, nativeQuery = true)
    void aggregateDaily(@Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);
}
