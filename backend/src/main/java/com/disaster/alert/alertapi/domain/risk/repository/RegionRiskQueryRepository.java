package com.disaster.alert.alertapi.domain.risk.repository;

import com.disaster.alert.alertapi.domain.risk.repository.projection.AlertRiskRow;
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

    /** 재난문자 단건 → 소속 이벤트 → 지역별 영향 행 목록. alert가 이벤트에 매핑되지 않았으면 빈 리스트 반환. */
    public List<AlertRiskRow> findByAlertId(Long alertId) {
        String sql = """
                SELECT
                    eam.event_id                  AS event_id,
                    de.event_title                AS event_title,
                    de.primary_disaster_type      AS disaster_type,
                    eri.region_code               AS region_code,
                    eri.impact_score              AS impact_score
                FROM event_alert_mapping eam
                JOIN disaster_events de  ON de.id           = eam.event_id
                JOIN event_region_impact eri ON eri.event_id = eam.event_id
                WHERE eam.alert_id = ?
                ORDER BY eri.impact_score DESC
                """;
        return jdbc.query(sql,
                (rs, n) -> new AlertRiskRow(
                        rs.getLong("event_id"),
                        rs.getString("event_title"),
                        rs.getString("disaster_type"),
                        rs.getString("region_code"),
                        rs.getDouble("impact_score")),
                alertId);
    }

    public List<ContributingEventRow> findHistoricalEvents(String sigunguCode, LocalDateTime start, LocalDateTime end) {
        // 위험도는 최대 7일(ACTIVE_WINDOW_DAYS) 동안 감쇠하며 여파를 미치므로,
        // 해당 기간에 위험도를 발생시킨 과거 원인 이벤트까지 찾기 위해 검색 시작일을 7일 전으로 확장합니다.
        LocalDateTime effectiveStart = start.minusDays(com.disaster.alert.alertapi.domain.risk.RiskConstants.ACTIVE_WINDOW_DAYS);
        
        String sql = """
                SELECT DISTINCT
                       e.id                   AS event_id,
                       e.event_title          AS event_title,
                       e.primary_disaster_type AS disaster_type,
                       eri.impact_score       AS impact_score
                FROM event_region_impact eri
                JOIN disaster_events e ON e.id = eri.event_id
                WHERE LEFT(eri.region_code, 5) = ?
                  AND e.first_alert_at <= ?
                  AND e.last_alert_at >= ?
                ORDER BY eri.impact_score DESC
                LIMIT 50
                """;
        return jdbc.query(sql,
                (rs, n) -> new ContributingEventRow(
                        rs.getLong("event_id"),
                        rs.getString("event_title"),
                        rs.getString("disaster_type"),
                        rs.getDouble("impact_score")),
                sigunguCode, Timestamp.valueOf(end), Timestamp.valueOf(effectiveStart));
    }
}
