package com.disaster.alert.alertapi.domain.event.evaluation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * DB 에 저장된 이벤트 배정과 쌍의 사실(유형·시군구·간격·본문 거리)을 읽는다. 조회 전용.
 */
// Red 단계 뼈대 — 구현은 Green 에서
@Component
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
            return "";
        }
    }

    private final JdbcTemplate jdbcTemplate;

    public AssignmentReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 알림이 속한 이벤트 id. 이벤트에 없으면 empty. */
    public Optional<Long> findEventId(long alertId) {
        return Optional.empty();
    }

    public PairFacts findFacts(long alertA, long alertB) {
        return new PairFacts("", "", false, 0L, null);
    }
}
