package com.disaster.alert.alertapi.domain.risk.repository.projection;

import java.time.LocalDateTime;

/**
 * findActiveBySigungu 네이티브 쿼리 결과 매핑용 인터페이스 projection.
 * getter 이름 = 쿼리 alias (eventId, impactScore, lastAlertAt, disasterType).
 */
public interface EventImpactRow {
    Long getEventId();
    Double getImpactScore();
    LocalDateTime getLastAlertAt();
    String getDisasterType();
}
