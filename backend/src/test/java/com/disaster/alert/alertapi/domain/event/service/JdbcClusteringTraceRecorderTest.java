package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JDBC 구현이 기록 1건을 clustering_trace 한 행으로 저장하는지 본다.
 * 이 빈은 backfill 프로필에서만 만들어지고 테스트는 test 프로필이라, 컨텍스트의 JdbcTemplate 으로 직접 생성한다.
 */
@IntegrationTest
class JdbcClusteringTraceRecorderTest {

    /** 실제 알림과 겹치지 않는 id (clustering_trace.alert_id 는 FK 가 아니다). */
    private static final long ALERT_ID = 987_654_321L;

    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM clustering_trace WHERE alert_id = ?", ALERT_ID);
    }

    // 결정: 기록 객체의 각 칸(null 포함)이 같은 이름의 컬럼에 그대로 저장되고 recorded_at 은 DB 쪽에서 채워진다.
    // Red: 뼈대는 INSERT 하지 않고 clustering_trace 테이블(V132, Green 몫)도 아직 없어서,
    //      아래 SELECT 가 "relation does not exist" SQL 오류로 실패한다(테이블이 생긴 뒤에도 행이 없어 단언에서 실패).
    @Test
    @DisplayName("기록 1건을 record() 하면 clustering_trace 에 같은 값으로 한 행이 저장된다")
    void record_savesOneRowWithSameValues() {
        JdbcClusteringTraceRecorder recorder = new JdbcClusteringTraceRecorder(jdbcTemplate);
        ClusteringTrace trace = new ClusteringTrace(ALERT_ID, ClusteringTrace.Path.EMBEDDING,
                "12:0.2100, 30:0.3300", null, 0.15, "후보 2건 → 없음",
                ClusteringTrace.Result.NEW, 900L, "LLM 호출: 사고성 유형");

        recorder.record(trace);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM clustering_trace WHERE alert_id = ?", ALERT_ID);
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("path")).isEqualTo("EMBEDDING");
        assertThat(row.get("candidates")).isEqualTo("12:0.2100, 30:0.3300");
        assertThat(row.get("adjacent_candidates")).isNull(); // 조회 안 한 칸은 null 로 저장
        assertThat(((Number) row.get("threshold")).doubleValue()).isEqualTo(0.15);
        assertThat(row.get("llm")).isEqualTo("후보 2건 → 없음");
        assertThat(row.get("result")).isEqualTo("NEW");
        assertThat(((Number) row.get("event_id")).longValue()).isEqualTo(900L);
        assertThat(row.get("note")).isEqualTo("LLM 호출: 사고성 유형");
        assertThat(row.get("recorded_at")).isNotNull();
    }
}
