package com.disaster.alert.alertapi.domain.event.evaluation;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * DB 에 저장된 이벤트 배정과 쌍의 사실(유형·시군구·간격·본문 거리)을 읽는다. 조회 전용.
 */
@Component
@RequiredArgsConstructor
public class AssignmentReader {

    /**
     * 쌍의 사실 4개. 이유를 계산하지 않고 값만 담는다.
     *
     * @param typeA             알림 A 의 재난 유형
     * @param typeB             알림 B 의 재난 유형
     * @param sameSigungu       법정동 코드 앞 5자리를 하나라도 공유하면 true
     * @param gapHours          발송 시각 차(시간, 반올림 정수)
     * @param embeddingDistance 본문 임베딩 코사인 거리, 임베딩이 없으면 null
     */
    public record PairFacts(String typeA, String typeB, boolean sameSigungu, long gapHours,
                            Double embeddingDistance) {

        /** 두 유형이 같으면 하나, 다르면 "A/B". */
        public String type() {
            return Objects.equals(typeA, typeB) ? String.valueOf(typeA) : typeA + "/" + typeB;
        }
    }

    // 한 알림은 하나의 이벤트에만 속한다(uq_event_alert_mapping_alert) - 행이 없으면 미배정
    private static final String EVENT_ID_SQL =
            "SELECT event_id FROM event_alert_mapping WHERE alert_id = ?";

    // 거리는 DB 의 <=> (코사인 거리)로 계산해 1536차원 벡터를 앱으로 가져오지 않는다.
    // 어느 한쪽 embedding 이 NULL 이면 <=> 도 NULL 이다. 시군구는 법정동 코드 앞 5자리 일치 여부.
    private static final String FACTS_SQL =
            "SELECT a.disaster_type AS type_a, b.disaster_type AS type_b, "
                    + "EXTRACT(EPOCH FROM (b.created_at - a.created_at)) AS gap_seconds, "
                    + "(a.embedding <=> b.embedding) AS distance, "
                    + "EXISTS (SELECT 1 FROM disaster_alert_region ra JOIN disaster_alert_region rb "
                    + "ON LEFT(ra.legal_district_code, 5) = LEFT(rb.legal_district_code, 5) "
                    + "WHERE ra.disaster_alert_id = a.disaster_alert_id "
                    + "AND rb.disaster_alert_id = b.disaster_alert_id) AS same_sigungu "
                    + "FROM disaster_alert a, disaster_alert b "
                    + "WHERE a.disaster_alert_id = ? AND b.disaster_alert_id = ?";

    private final JdbcTemplate jdbcTemplate;

    /** 알림이 속한 이벤트 id. 이벤트에 없으면 empty. */
    public Optional<Long> findEventId(long alertId) {
        List<Long> ids = jdbcTemplate.queryForList(EVENT_ID_SQL, Long.class, alertId);
        return ids.stream().findFirst();
    }

    /** 쌍의 사실. 알림 id 가 DB 에 없으면 라벨 CSV 와 DB 가 어긋난 것이므로 예외. */
    public PairFacts findFacts(long alertA, long alertB) {
        try {
            return jdbcTemplate.queryForObject(FACTS_SQL, (rs, i) -> {
                // 간격은 순서와 무관하게 절댓값을 시간 단위로 반올림한다
                long gapHours = Math.round(Math.abs(rs.getDouble("gap_seconds")) / 3600.0);
                double distance = rs.getDouble("distance");
                // getDouble 은 NULL 을 0.0 으로 돌려주므로 wasNull 로 "임베딩 없음" 을 구분한다
                Double embeddingDistance = rs.wasNull() ? null : distance;
                return new PairFacts(rs.getString("type_a"), rs.getString("type_b"),
                        rs.getBoolean("same_sigungu"), gapHours, embeddingDistance);
            }, alertA, alertB);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalStateException("DB 에 없는 알림이 라벨에 있습니다: " + alertA + ", " + alertB, e);
        }
    }
}
