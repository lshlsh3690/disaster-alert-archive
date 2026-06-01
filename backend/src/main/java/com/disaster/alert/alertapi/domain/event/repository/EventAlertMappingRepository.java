package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.dto.EventTimelineRow;
import com.disaster.alert.alertapi.domain.event.model.EventAlertMapping;
import com.disaster.alert.alertapi.domain.event.model.EventAlertMappingId;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
