package com.disaster.alert.alertapi.domain.risk.repository;

import com.disaster.alert.alertapi.domain.risk.repository.projection.ContributingEventRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.List;

/**
 * region_risk_index 는 top_event_id 하나만 저장하므로, 상세 화면의 "기여 이벤트 목록"은
 * event_region_impact 를 active event 와 조인해 별도 조회한다(검증 minor 항목).
 *
 * <p>감쇠 적용 점수 기준 정렬은 단순화를 위해 base impact_score 기준으로 함(상세 표시용).
 */
@Repository
@RequiredArgsConstructor
public class RegionRiskQueryRepository {

    private final JdbcTemplate jdbc;

    public List<ContributingEventRow> findContributingEvents(String sigunguCode, LocalDateTime since) {
        String sql = """
                SELECT DISTINCT
                       e.id                   AS event_id,
                       e.event_title          AS event_title,
                       e.primary_disaster_type AS disaster_type,
                       eri.impact_score       AS impact_score
                FROM event_region_impact eri
                JOIN disaster_events e ON e.id = eri.event_id
                WHERE LEFT(eri.region_code, 5) = ?
                  AND e.last_alert_at > ?
                ORDER BY eri.impact_score DESC
                LIMIT 20
                """;
        return jdbc.query(sql,
                (rs, n) -> new ContributingEventRow(
                        rs.getLong("event_id"),
                        rs.getString("event_title"),
                        rs.getString("disaster_type"),
                        rs.getDouble("impact_score")),
                sigunguCode, Timestamp.valueOf(since));
    }
}
