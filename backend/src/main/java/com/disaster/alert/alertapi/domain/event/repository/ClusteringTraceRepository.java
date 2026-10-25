package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.model.ClusteringTrace;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * clustering_trace(클러스터링 판정 기록) 전용 JdbcTemplate 접근. 설계는
 * docs/event-clustering/ADMIN_EVAL_DASHBOARD_DESIGN.md 4절 ① 참고.
 */
// Red 단계 뼈대 — 구현은 Green 에서
@Repository
public class ClusteringTraceRepository {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public ClusteringTraceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 알림당 1줄로 판정 기록을 저장한다(이미 있으면 덮어씀).
     *
     * <p>REQUIRES_NEW 인 이유: 클러스터링 트랜잭션 안에서 이 SQL 이 실패하면 Postgres 가 트랜잭션 전체를 중단 상태로
     * 만들어 클러스터링 결과(매핑·이벤트)까지 커밋되지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsert(ClusteringTrace trace) {
        // 아무것도 하지 않는다 — UPSERT SQL 과 JSON 직렬화는 Green 에서.
    }
}
