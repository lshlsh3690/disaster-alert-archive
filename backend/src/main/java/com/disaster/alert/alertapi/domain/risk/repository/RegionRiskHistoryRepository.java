package com.disaster.alert.alertapi.domain.risk.repository;

import com.disaster.alert.alertapi.domain.risk.model.RegionRiskHistory;
import com.disaster.alert.alertapi.domain.risk.model.RegionRiskHistoryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RegionRiskHistoryRepository
        extends JpaRepository<RegionRiskHistory, RegionRiskHistoryId> {

    /** 특정 시군구 시계열 (트렌드 차트 / Phase 2 Chronos 입력). */
    @Query("""
            SELECT h FROM RegionRiskHistory h
            WHERE h.id.regionCode = :regionCode
              AND h.id.snapshotAt > :since
            ORDER BY h.id.snapshotAt ASC
            """)
    List<RegionRiskHistory> findSeries(@Param("regionCode") String regionCode,
                                       @Param("since") LocalDateTime since);

    /** 90일 이상 된 스냅샷 정리 (retention). */
    @Modifying
    @Query("DELETE FROM RegionRiskHistory h WHERE h.id.snapshotAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
