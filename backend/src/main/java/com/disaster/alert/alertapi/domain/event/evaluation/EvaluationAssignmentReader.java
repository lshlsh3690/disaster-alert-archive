package com.disaster.alert.alertapi.domain.event.evaluation;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 평가에 필요한 기존 이벤트 배정·후보 검색을 SQL 로 읽는 조회 전용 클래스.
 *
 * <p>pgvector 거리 정렬과 시군구 EXISTS 를 앱 메모리로 옮기면 쌍마다 임베딩(1536차원)을 끌어와야 하므로,
 * b 의 임베딩은 서브쿼리로만 쓰고 앱으로 가져오지 않는다.
 */
@Component
@RequiredArgsConstructor
public class EvaluationAssignmentReader {

    /** 알림의 시각과 임베딩 보유 여부 — 임베딩 값 자체는 필요 없다. */
    public record AlertSnapshot(LocalDateTime createdAt, boolean hasEmbedding) {
    }

    /** b 시점 후보 중 가장 가까운 이벤트와 그 이벤트의 최소 임베딩 코사인 거리. */
    public record CandidateDistance(long eventId, double distance) {
    }

    private final JdbcTemplate jdbc;

    private static final String MIN_DISTANCE = "MIN(da.embedding <=> b.embedding)";

    // b 의 임베딩은 JOIN 으로만 참조해 앱으로 가져오지 않는다. 파라미터 순서: b id, before, before, since, b id.
    private static final String CANDIDATE_FROM_WHERE = """
            FROM disaster_events e
            JOIN event_alert_mapping m ON m.event_id = e.id
            JOIN disaster_alert da ON da.disaster_alert_id = m.alert_id
            JOIN disaster_alert b ON b.disaster_alert_id = ?
            WHERE e.is_broadcast = false
              AND e.is_advisory = false
              AND da.created_at < ?
              AND da.embedding IS NOT NULL
              AND EXISTS (
                  SELECT 1 FROM event_alert_mapping m3
                  JOIN disaster_alert d3 ON d3.disaster_alert_id = m3.alert_id
                  WHERE m3.event_id = e.id AND d3.created_at < ? AND d3.created_at > ?
              )
              AND EXISTS (
                  SELECT 1 FROM disaster_alert_region dar
                  WHERE dar.disaster_alert_id = da.disaster_alert_id
                    AND LEFT(dar.legal_district_code, 5) IN (
                        SELECT LEFT(br.legal_district_code, 5)
                        FROM disaster_alert_region br WHERE br.disaster_alert_id = ?)
              )
            GROUP BY e.id
            """;


    public Optional<AlertSnapshot> findSnapshot(long alertId) {
        return jdbc.query("""
                        SELECT created_at, embedding IS NOT NULL
                        FROM disaster_alert WHERE disaster_alert_id = ?
                        """,
                (rs, i) -> new AlertSnapshot(rs.getTimestamp(1).toLocalDateTime(), rs.getBoolean(2)),
                alertId).stream().findFirst();
    }

    // Red 단계 뼈대 — 구현은 Green 에서
    /** 두 알림의 유형·시군구 공유·발송 간격·본문 거리. 알림이 없으면 empty. */
    public Optional<PairFacts> findPairFacts(long alertAId, long alertBId) {
        return Optional.empty();
    }

    /** 알림이 배정된 이벤트 id. 매핑이 없으면 empty. */
    public Optional<Long> findEventId(long alertId) {
        return jdbc.queryForList(
                "SELECT event_id FROM event_alert_mapping WHERE alert_id = ? ORDER BY event_id LIMIT 1",
                Long.class, alertId).stream().findFirst();
    }

    /**
     * b 시점의 후보 이벤트 top-3 를 현재 이벤트 상태에서 근사해 재현한다.
     * {@code DisasterEventRepository.findTopCandidates} 와 같은 의미(이벤트별 MIN 거리, 시군구 EXISTS 필터,
     * broadcast/advisory 제외)이되, 이벤트의 last_alert_at 대신 "b 이전 알림만"으로 윈도우를 판정한다.
     * 이벤트가 b 이후 알림으로 윈도우 안에 들어와 있는 경우를 후보로 잘못 세지 않기 위해서다.
     * 동률은 event id 오름차순으로 결정적이다.
     */
    public List<Long> findTopCandidateEventIds(long alertBId, LocalDateTime bCreatedAt, int windowHours) {
        return jdbc.queryForList(
                "SELECT e.id " + CANDIDATE_FROM_WHERE + " ORDER BY " + MIN_DISTANCE + " ASC, e.id ASC LIMIT 3",
                Long.class, candidateParams(alertBId, bCreatedAt, windowHours));
    }

    /**
     * {@link #findTopCandidateEventIds} 와 같은 후보 정의에서 top-1 이벤트와 그 최소 거리를 돌려준다.
     * 거리는 DB 가 계산한 double 을 그대로 넘긴다(임계값 경계 비교를 float 반올림으로 흐리지 않기 위해).
     */
    public Optional<CandidateDistance> findTopCandidate(long alertBId, LocalDateTime bCreatedAt, int windowHours) {
        return jdbc.query(
                "SELECT e.id, " + MIN_DISTANCE + " " + CANDIDATE_FROM_WHERE + " ORDER BY 2 ASC, e.id ASC LIMIT 1",
                (rs, i) -> new CandidateDistance(rs.getLong(1), rs.getDouble(2)),
                candidateParams(alertBId, bCreatedAt, windowHours)).stream().findFirst();
    }

    private static Object[] candidateParams(long alertBId, LocalDateTime bCreatedAt, int windowHours) {
        Timestamp before = Timestamp.valueOf(bCreatedAt);
        Timestamp since = Timestamp.valueOf(bCreatedAt.minusHours(windowHours));
        return new Object[]{alertBId, before, before, since, alertBId};
    }
}
