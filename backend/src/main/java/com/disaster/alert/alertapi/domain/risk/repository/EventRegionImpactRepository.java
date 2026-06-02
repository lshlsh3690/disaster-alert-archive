package com.disaster.alert.alertapi.domain.risk.repository;

import com.disaster.alert.alertapi.domain.risk.model.EventRegionImpact;
import com.disaster.alert.alertapi.domain.risk.model.EventRegionImpactId;
import com.disaster.alert.alertapi.domain.risk.repository.projection.EventImpactRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EventRegionImpactRepository
        extends JpaRepository<EventRegionImpact, EventRegionImpactId> {

    /** 이벤트→법정동 영향 upsert (event 단위 재계산 시 멱등). */
    @Modifying
    @Query(value = """
            INSERT INTO event_region_impact (event_id, region_code, impact_score, created_at)
            VALUES (:eventId, :regionCode, :score, NOW())
            ON CONFLICT (event_id, region_code)
            DO UPDATE SET impact_score = :score, created_at = NOW()
            """, nativeQuery = true)
    void upsert(@Param("eventId") Long eventId,
                @Param("regionCode") String regionCode,
                @Param("score") double score);

    /**
     * 특정 시군구에 영향을 주는 활성 이벤트들.
     * 법정동 region_code 앞 5자리로 시군구 매칭. event 당 한 행(impact_score 는 event 내 동일).
     * sinceTime 이후 last_alert_at 인 이벤트만 (오래된 건 감쇠로 무의미).
     */
    @Query(value = """
            SELECT DISTINCT
                   eri.event_id            AS eventId,
                   eri.impact_score        AS impactScore,
                   e.last_alert_at         AS lastAlertAt,
                   e.primary_disaster_type AS disasterType
            FROM event_region_impact eri
            JOIN disaster_events e ON e.id = eri.event_id
            WHERE LEFT(eri.region_code, 5) = :sigungu
              AND e.last_alert_at > :since
            """, nativeQuery = true)
    List<EventImpactRow> findActiveBySigungu(@Param("sigungu") String sigunguCode,
                                             @Param("since") LocalDateTime since);

    /** 활성 영향이 있는 시군구 코드 목록 (스케줄러 재계산 대상 1). */
    @Query(value = """
            SELECT DISTINCT LEFT(eri.region_code, 5)
            FROM event_region_impact eri
            JOIN disaster_events e ON e.id = eri.event_id
            WHERE e.last_alert_at > :since
            """, nativeQuery = true)
    List<String> findActiveSigunguCodes(@Param("since") LocalDateTime since);
}
