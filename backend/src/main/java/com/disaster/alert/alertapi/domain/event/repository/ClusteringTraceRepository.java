package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.model.ClusteringTrace;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * clustering_trace(클러스터링 판정 기록) 전용 JdbcTemplate 접근. 설계는
 * docs/event-clustering/ADMIN_EVAL_DASHBOARD_DESIGN.md 4절 ① 참고.
 */
@Repository
public class ClusteringTraceRepository {

    // 알림당 1줄(alert_id PK). 같은 알림을 다시 클러스터링(백필)하면 모든 컬럼을 새 값으로 덮어쓴다.
    // 후보 컬럼은 JSONB 라 문자열 파라미터를 CAST 로 넘긴다(null 이면 JSONB NULL).
    private static final String UPSERT_SQL = """
            INSERT INTO clustering_trace
                (alert_id, path, candidates, adjacent_candidates, threshold, llm_called, llm_result, result, event_id, created_at)
            VALUES (?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, ?, ?, now())
            ON CONFLICT (alert_id) DO UPDATE SET
                path = EXCLUDED.path,
                candidates = EXCLUDED.candidates,
                adjacent_candidates = EXCLUDED.adjacent_candidates,
                threshold = EXCLUDED.threshold,
                llm_called = EXCLUDED.llm_called,
                llm_result = EXCLUDED.llm_result,
                result = EXCLUDED.result,
                event_id = EXCLUDED.event_id,
                created_at = now()
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public ClusteringTraceRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 알림당 1줄로 판정 기록을 저장한다(이미 있으면 덮어씀).
     *
     * <p>REQUIRES_NEW 인 이유: 클러스터링 트랜잭션 안에서 이 SQL 이 실패하면 Postgres 가 트랜잭션 전체를 중단 상태로
     * 만들어 클러스터링 결과(매핑·이벤트)까지 커밋되지 않는다.
     * 행 잠금 자기 교착이 없는 전제: 바깥 트랜잭션은 clustering_trace 를 건드리지 않고(외래 키도 없음) 호출부
     * (DisasterFetchScheduler·EventClusteringBackfillTool)가 순차라 스레드당 커넥션 2개까지만 쓴다 — 호출부를 병렬화하면 다시 볼 것.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsert(ClusteringTrace trace) {
        // JSON 직렬화는 SQL 실행 전에 끝낸다 - 직렬화 실패가 반쯤 열린 문장을 남기지 않게.
        String candidatesJson = toJson(trace.getCandidates());
        String adjacentJson = toJson(trace.getAdjacentCandidates());
        // null 가능 컬럼(threshold·llm_result·event_id·후보)은 타입을 명시해 setNull 한다 - 타입 없는 null 은 드라이버마다 다르게 해석된다.
        jdbcTemplate.update(UPSERT_SQL, ps -> {
            ps.setLong(1, trace.getAlertId());
            ps.setString(2, trace.getPath().name());
            ps.setObject(3, candidatesJson, Types.VARCHAR);
            ps.setObject(4, adjacentJson, Types.VARCHAR);
            ps.setObject(5, trace.getThreshold(), Types.DOUBLE);
            ps.setBoolean(6, trace.isLlmCalled());
            ps.setObject(7, trace.getLlmResult(), Types.BIGINT);
            ps.setString(8, trace.getResult().name());
            ps.setObject(9, trace.getEventId(), Types.BIGINT);
        });
    }

    /**
     * 후보 목록을 {@code [{"event":id,"dist":값}]} 로 직렬화한다. 대시보드 SQL 이 이 키 이름으로 읽는다.
     * null(검색하지 않음)은 null 그대로, 빈 목록(검색했는데 0건)은 {@code []} 로 구분해 남긴다.
     */
    private String toJson(List<ClusteringTrace.Candidate> candidates) {
        if (candidates == null) {
            return null;
        }
        List<Map<String, Object>> rows = candidates.stream().map(c -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("event", c.eventId());
            row.put("dist", c.dist());
            return row;
        }).toList();
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (JsonProcessingException e) {
            // Long/double 만 담은 Map 이라 사실상 발생하지 않는다. 발생하면 호출부(saveTrace)가 warn 으로 삼킨다.
            throw new IllegalStateException("판정 기록 후보 직렬화 실패", e);
        }
    }
}
