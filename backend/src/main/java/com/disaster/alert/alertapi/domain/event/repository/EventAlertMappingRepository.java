package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.model.EventAlertMapping;
import com.disaster.alert.alertapi.domain.event.model.EventAlertMappingId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventAlertMappingRepository extends JpaRepository<EventAlertMapping, EventAlertMappingId> {

    /**
     * 이벤트에 다음으로 부여할 sequence_no 계산용 — 현재 매핑 수.
     * 0건이면 신규 이벤트 첫 알림 (sequence_no = 1).
     */
    @Query(value = "SELECT COUNT(*) FROM event_alert_mapping WHERE event_id = :eventId", nativeQuery = true)
    int countByEventId(@Param("eventId") Long eventId);
}
