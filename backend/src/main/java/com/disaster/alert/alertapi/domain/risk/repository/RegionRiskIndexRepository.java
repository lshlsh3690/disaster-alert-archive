package com.disaster.alert.alertapi.domain.risk.repository;

import com.disaster.alert.alertapi.domain.risk.model.RegionRiskIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RegionRiskIndexRepository extends JpaRepository<RegionRiskIndex, String> {

    @Modifying
    @Query(value = """
            INSERT INTO region_risk_index (region_code, risk_score, top_event_id, updated_at)
            VALUES (:regionCode, :score, :topEventId, :updatedAt)
            ON CONFLICT (region_code)
            DO UPDATE SET risk_score = :score,
                          top_event_id = :topEventId,
                          updated_at = :updatedAt
            """, nativeQuery = true)
    void upsert(@Param("regionCode") String regionCode,
                @Param("score") double score,
                @Param("topEventId") Long topEventId,
                @Param("updatedAt") LocalDateTime updatedAt);

    /** 0보다 큰 점수를 가진 시군구 (스케줄러 재계산 대상 2 — 만료 지역 zeroing 용). */
    @Query("SELECT r.regionCode FROM RegionRiskIndex r WHERE r.riskScore > 0")
    List<String> findNonZeroRegionCodes();

    /** nonzero 지역 전체 (history 스냅샷용). */
    @Query("SELECT r FROM RegionRiskIndex r WHERE r.riskScore > 0")
    List<RegionRiskIndex> findAllNonZero();
}
