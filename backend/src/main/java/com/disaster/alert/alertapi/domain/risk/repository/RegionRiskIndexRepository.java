package com.disaster.alert.alertapi.domain.risk.repository;

import com.disaster.alert.alertapi.domain.risk.model.RegionRiskIndex;
import com.disaster.alert.alertapi.domain.risk.repository.projection.RiskSourceRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RegionRiskIndexRepository extends JpaRepository<RegionRiskIndex, String> {

    /**
     * source(자기, 전파 전) 점수 upsert. risk_score(effective)는 건드리지 않는다 —
     * 그건 propagateEffective 가 전체 그래프 전파 후 별도로 채운다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO region_risk_index (region_code, source_score, source_top_event_id, risk_score, updated_at)
            VALUES (:regionCode, :score, :topEventId, 0.0, :updatedAt)
            ON CONFLICT (region_code)
            DO UPDATE SET source_score = :score,
                          source_top_event_id = :topEventId,
                          updated_at = :updatedAt
            """, nativeQuery = true)
    void upsertSource(@Param("regionCode") String regionCode,
                      @Param("score") double score,
                      @Param("topEventId") Long topEventId,
                      @Param("updatedAt") LocalDateTime updatedAt);

    /** effective(전파 후) 점수 upsert. FE 가 읽는 risk_score 갱신. */
    @Modifying
    @Query(value = """
            INSERT INTO region_risk_index (region_code, risk_score, top_event_id, updated_at)
            VALUES (:regionCode, :score, :topEventId, :updatedAt)
            ON CONFLICT (region_code)
            DO UPDATE SET risk_score = :score,
                          top_event_id = :topEventId,
                          updated_at = :updatedAt
            """, nativeQuery = true)
    void upsertEffective(@Param("regionCode") String regionCode,
                         @Param("score") double score,
                         @Param("topEventId") Long topEventId,
                         @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 확산 전파 입력 로드 — source_score>0 인 시군구 전체 + 진원 유형의 확산계수.
     * UNKNOWN 유형은 '기타' 프로파일로 매핑, 매칭 없으면 기본 0.3.
     */
    @Query(value = """
            SELECT i.region_code         AS regionCode,
                   i.source_score        AS sourceScore,
                   i.source_top_event_id AS sourceTopEventId,
                   COALESCE(p.spread_coeff, 0.3) AS spreadCoeff
            FROM region_risk_index i
            LEFT JOIN disaster_events e ON e.id = i.source_top_event_id
            LEFT JOIN disaster_risk_profile p
                   ON p.disaster_type = CASE WHEN e.primary_disaster_type = 'UNKNOWN'
                                             THEN '기타' ELSE e.primary_disaster_type END
            WHERE i.source_score > 0
            """, nativeQuery = true)
    List<RiskSourceRow> findSourcesForPropagation();

    /** index 에 있는 모든 시군구 코드 (전파에서 도달 못한 곳 effective=0 으로 내리기 위함). */
    @Query("SELECT r.regionCode FROM RegionRiskIndex r")
    List<String> findAllRegionCodes();

    /** 0보다 큰 점수를 가진 시군구 (스케줄러 재계산 대상 2 — 만료 지역 zeroing 용). */
    @Query("SELECT r.regionCode FROM RegionRiskIndex r WHERE r.riskScore > 0")
    List<String> findNonZeroRegionCodes();

    /** nonzero 지역 전체 (history 스냅샷용). */
    @Query("SELECT r FROM RegionRiskIndex r WHERE r.riskScore > 0")
    List<RegionRiskIndex> findAllNonZero();
}
