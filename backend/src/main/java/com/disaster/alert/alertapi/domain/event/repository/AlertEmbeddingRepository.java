package com.disaster.alert.alertapi.domain.event.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * disaster_alert.embedding 컬럼 전용 native 접근.
 *
 * <p>임베딩 컬럼은 JPA 엔티티({@code DisasterAlert})에 매핑하지 않는다. 이유:
 * <ul>
 *   <li>pgvector 의 {@code VECTOR(1536)} 타입은 Hibernate 가 기본 인식 못 함 (별도 UserType 필요)</li>
 *   <li>읽기는 native distance 쿼리({@link DisasterEventRepository}) 로만 발생</li>
 *   <li>쓰기는 이 클래스의 명시적 UPDATE 로만 발생</li>
 * </ul>
 *
 * <p>JPA validate 모드는 엔티티에서 매핑하지 않은 DB 컬럼을 문제삼지 않는다.
 */
@Repository
public class AlertEmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public AlertEmbeddingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 단건 임베딩 저장. {@code embedding} 은 pgvector text 표현 ("[0.1, 0.2, ...]").
     */
    public void updateEmbedding(Long alertId, String embedding) {
        jdbcTemplate.update(
                "UPDATE disaster_alert SET embedding = CAST(? AS vector) WHERE disaster_alert_id = ?",
                embedding, alertId
        );
    }

    /**
     * 저장된 임베딩을 pgvector text 표현("[0.1,0.2,...]")으로 조회. 없으면 null.
     *
     * <p>재클러스터링(임계값 튜닝) 시 OpenAI 재호출 없이 저장된 벡터를 그대로 후보 검색에 쓰기 위함.
     */
    public String findEmbeddingText(Long alertId) {
        var result = jdbcTemplate.query(
                "SELECT embedding::text FROM disaster_alert WHERE disaster_alert_id = ?",
                (rs, rowNum) -> rs.getString(1),
                alertId
        );
        return result.isEmpty() ? null : result.get(0);
    }
}
