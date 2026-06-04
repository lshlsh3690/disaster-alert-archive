package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.dto.EventTimelineRow;
import com.disaster.alert.alertapi.domain.event.model.EventAlertMapping;
import com.disaster.alert.alertapi.domain.event.model.EventAlertMappingId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventAlertMappingRepository extends JpaRepository<EventAlertMapping, EventAlertMappingId> {

    /**
     * 이벤트에 다음으로 부여할 sequence_no 계산용 — 현재 매핑 수.
     * 0건이면 신규 이벤트 첫 알림 (sequence_no = 1).
     */
    @Query(value = "SELECT COUNT(*) FROM event_alert_mapping WHERE event_id = :eventId", nativeQuery = true)
    int countByEventId(@Param("eventId") Long eventId);

    /**
     * 이벤트 타임라인 — 소속 재난문자를 sequence_no 순으로.
     *
     * <p>EventAlertMapping ↔ DisasterAlert 는 매핑 관계가 없어 ad-hoc JOIN(ON) 으로 연결.
     * 지역명은 빠져 있으며 서비스에서 batch 조회 후 합친다.
     */
    @Query("""
            SELECT new com.disaster.alert.alertapi.domain.event.dto.EventTimelineRow(
                da.id, m.sequenceNo, da.message, da.disasterType, da.emergencyLevel, da.createdAt)
            FROM EventAlertMapping m
            JOIN DisasterAlert da ON da.id = m.id.alertId
            WHERE m.id.eventId = :eventId
            ORDER BY m.sequenceNo ASC
            """)
    List<EventTimelineRow> findTimelineRows(@Param("eventId") Long eventId);

    // ── cross-region LLM 병합 보조 ─────────────────────────────────

    /** 알림이 현재 속한 이벤트 id (없으면 null — 아직 클러스터링 안 됨). */
    @Query(value = "SELECT event_id FROM event_alert_mapping WHERE alert_id = :alertId", nativeQuery = true)
    Long findEventIdByAlertId(@Param("alertId") Long alertId);

    /** 이벤트가 걸친 distinct 시도 수 (법정동 코드 앞 2자리) — span-cap 판정용. */
    @Query(value = """
            SELECT count(DISTINCT left(dar.legal_district_code, 2))
            FROM event_alert_mapping m
            JOIN disaster_alert_region dar ON dar.disaster_alert_id = m.alert_id
            WHERE m.event_id = :eventId
            """, nativeQuery = true)
    int countDistinctSidoByEventId(@Param("eventId") Long eventId);

    /** 후보 이벤트들의 대표(seq=1, seed) 알림 본문 — LLM 프롬프트용. 반환 {event_id, message}. */
    @Query(value = """
            SELECT m.event_id, da.message
            FROM event_alert_mapping m
            JOIN disaster_alert da ON da.disaster_alert_id = m.alert_id
            WHERE m.event_id IN (:eventIds) AND m.sequence_no = 1
            """, nativeQuery = true)
    List<Object[]> findRepresentativeMessages(@Param("eventIds") List<Long> eventIds);

    /**
     * source 이벤트의 모든 매핑을 target 이벤트로 재배치 (cross-region 병합 primitive).
     * sequence_no 는 target 의 기존 매핑 수만큼 밀어 이어붙이고, merge_method 를 지정.
     */
    @Modifying
    @Query(value = """
            UPDATE event_alert_mapping
            SET event_id = :targetEventId,
                sequence_no = sequence_no + :offset,
                merge_method = :method
            WHERE event_id = :sourceEventId
            """, nativeQuery = true)
    int reassignToEvent(@Param("sourceEventId") Long sourceEventId,
                        @Param("targetEventId") Long targetEventId,
                        @Param("offset") int offset,
                        @Param("method") String method);
}
