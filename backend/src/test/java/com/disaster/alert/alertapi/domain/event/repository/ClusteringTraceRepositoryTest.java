package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.model.ClusteringTrace;
import com.disaster.alert.alertapi.domain.event.model.ClusteringTracePath;
import com.disaster.alert.alertapi.domain.event.model.ClusteringTraceResult;
import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * clustering_trace UPSERT 를 실제 DB 로 검증한다. 외래 키가 없어 실제 알림 없이도 저장되므로
 * 다른 데이터와 충돌하지 않을 큰 alertId 를 쓴다.
 *
 * <p>upsert 는 REQUIRES_NEW 로 커밋되므로 테스트 트랜잭션 롤백에 기댈 수 없다 - 전후로 직접 지운다.
 *
 * <p>Red 단계에서는 upsert 가 아무것도 저장하지 않아 조회 결과가 0줄이고, 첫 단언(hasSize(1))에서 실패한다.
 */
@IntegrationTest
class ClusteringTraceRepositoryTest {

    private static final long ALERT_ID = 990_000_001L;

    @Autowired ClusteringTraceRepository repository;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM clustering_trace WHERE alert_id = ?", ALERT_ID);
    }

    private static ClusteringTrace embeddingTrace(long eventId) {
        ClusteringTrace t = new ClusteringTrace(ALERT_ID);
        t.recordPath(ClusteringTracePath.EMBEDDING);
        t.recordCandidates(List.of(new ClusteringTrace.Candidate(4242L, 0.387)));
        t.recordThreshold(0.15);
        t.recordResult(ClusteringTraceResult.NEW, eventId);
        return t;
    }

    // 결정: 같은 알림을 다시 클러스터링(백필)하면 줄이 늘어나지 않고 덮어써져 알림당 1줄을 유지한다.
    //       덮어쓸 때 후보가 사라진(null) 경로로 바뀌면 이전 후보 JSON 이 남아 있으면 안 된다.
    // Red: upsert 가 no-op 이라 조회 결과가 0줄 - hasSize(1) 에서 실패한다.
    @Test
    @DisplayName("같은 alertId 를 두 번 저장하면 1줄만 남고 값은 2회차로 덮어써진다(후보도 null 로)")
    void upsertTwice_keepsSingleRowWithLatestValues() {
        // 1회차: 임베딩 경로, 후보 1개, 이벤트 1번에 신규.
        repository.upsert(embeddingTrace(1L));
        // 2회차: 지역 한정 경로 병합, 후보 없음(null), 이벤트 2번.
        ClusteringTrace second = new ClusteringTrace(ALERT_ID);
        second.recordPath(ClusteringTracePath.REGIONAL);
        second.recordResult(ClusteringTraceResult.MERGED, 2L);
        repository.upsert(second);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT path, result, event_id, candidates IS NULL AS candidates_null "
                        + "FROM clustering_trace WHERE alert_id = ?", ALERT_ID);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("path")).isEqualTo("REGIONAL");
        assertThat(rows.get(0).get("result")).isEqualTo("MERGED");
        assertThat(((Number) rows.get(0).get("event_id")).longValue()).isEqualTo(2L);
        assertThat(rows.get(0).get("candidates_null")).isEqualTo(true);
    }

    // 결정: 후보 JSON 의 키 이름은 설계안 표기 {"event":id,"dist":값} 로 고정한다 - 대시보드 SQL 이 이 키로 읽는다.
    //       eventId/dist 같은 자바 필드 이름이 그대로 새면 SQL 이 조용히 null 만 읽는다.
    // Red: upsert 가 no-op 이라 조회 결과가 0줄 - hasSize(1) 에서 실패한다.
    @Test
    @DisplayName("candidates JSONB 는 event/dist 키를 가진 객체 배열 형태로 저장된다")
    void upsert_storesCandidatesAsEventDistJson() {
        repository.upsert(embeddingTrace(1L));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT jsonb_array_length(candidates) AS n, candidates->0->>'event' AS ev, "
                        + "(candidates->0->>'dist')::float8 AS dist FROM clustering_trace WHERE alert_id = ?", ALERT_ID);

        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.get(0).get("n")).intValue()).isEqualTo(1);
        assertThat(rows.get(0).get("ev")).isEqualTo("4242");
        assertThat(((Number) rows.get(0).get("dist")).doubleValue()).isCloseTo(0.387, within(1e-9));
    }
}
