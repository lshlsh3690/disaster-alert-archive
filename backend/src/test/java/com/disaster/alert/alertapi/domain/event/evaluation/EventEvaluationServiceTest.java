package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 운영 사본 DB 의 기존 이벤트 배정을 라벨과 비교하는 {@link EventEvaluationService} 를 실제 DB 로 검증한다.
 * 평가는 "두 알림이 같은 이벤트에 있는가"만 보고, 원인 대신 사실(유형·시군구·간격·본문 거리)을 붙인다.
 *
 * <p>클래스에 {@code @Transactional} 을 붙이지 않고, 테스트 전용 dataset 이름과 직접 INSERT 한 행만
 * 매 테스트 후 JdbcTemplate 으로 정리한다. 시각은 모두 기준 시각 {@link #T}(= b 알림 시각) 상대값이다.
 */
@IntegrationTest
class EventEvaluationServiceTest {

    private static final LocalDateTime T = LocalDateTime.of(2020, 3, 10, 12, 0);
    private static final int DIM = 1536;
    private static final AtomicLong ID = new AtomicLong(960_000_000_000L + System.currentTimeMillis() % 1_000_000L * 1000);

    @Autowired
    private EventEvaluationService service;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> alertIds = new ArrayList<>();
    private final List<Long> eventIds = new ArrayList<>();
    private final List<String> datasets = new ArrayList<>();
    private String dataset;
    private String sigungu1;
    private String sigungu2;

    @BeforeEach
    void 준비() {
        dataset = "test-eval-" + UUID.randomUUID().toString().substring(0, 8);
        datasets.add(dataset);
        // legal_district 시드에 실제로 존재하는, 앞 5자가 서로 다른 두 시군구 코드 (FK)
        sigungu1 = anyDistrictCodeOfSigungu("11110");
        sigungu2 = anyDistrictCodeOfSigungu("11140");
    }

    @AfterEach
    void 정리() {
        // FK 순서: 라벨/매핑/지역 -> 이벤트/알림
        datasets.forEach(d -> jdbc.update("delete from evaluation_label where dataset = ?", d));
        alertIds.forEach(id -> {
            jdbc.update("delete from event_alert_mapping where alert_id = ?", id);
            jdbc.update("delete from disaster_alert_region where disaster_alert_id = ?", id);
        });
        eventIds.forEach(id -> jdbc.update("delete from disaster_events where id = ?", id));
        alertIds.forEach(id -> jdbc.update("delete from disaster_alert where disaster_alert_id = ?", id));
    }

    /**
     * 같은 이벤트에 있는 SAME 쌍은 "묶음", 다른 이벤트에 있는 SAME 쌍은 "놓침"으로 센다.
     * Red: 뼈대 평가기는 항상 0 을 돌려줘 merged=0, missed=0 이라 두 단언 모두 실패한다.
     */
    @Test
    void 같은_이벤트의_SAME_쌍은_묶음_다른_이벤트의_SAME_쌍은_놓침으로_센다() {
        // 준비: 쌍1 은 한 이벤트에 함께, 쌍2 는 각자 다른 이벤트에 있다
        long e = event(false, false);
        long a1 = alert(T.minusHours(2), sigungu1, vec(1, 0));
        long b1 = alert(T, sigungu1, vec(0.9, 0.1));
        map(e, a1, 1);
        map(e, b1, 2);
        long a2 = alert(T.minusHours(2), sigungu1, vec(1, 0));
        long b2 = alert(T, sigungu1, vec(0.9, 0.1));
        map(event(false, false), a2, 1);
        map(event(false, false), b2, 1);
        label(a1, b1, EvaluationLabelType.SAME);
        label(a2, b2, EvaluationLabelType.SAME);

        PairwiseReport report = service.evaluate(dataset);

        // 같은 이벤트 1쌍 = 묶음, 다른 이벤트 1쌍 = 놓침, DIFF 는 없으니 잘못 묶음 0
        assertEquals(1, report.merged());
        assertEquals(1, report.missed());
        assertEquals(0, report.wrongMerged());
    }

    /**
     * 한쪽 알림이 이벤트에 배정되지 않은 쌍(클러스터링에서 제외된 비사건 등)은 시스템이 아무것도 안 한 것이라
     * 묶음·놓침에 넣지 않고 excludedUnassigned 로만 센다.
     * Red: 서비스는 assigned 를 채우지만 평가기 뼈대가 0 만 돌려줘 excludedUnassigned 가 0 이다.
     */
    @Test
    void 한쪽_알림이_이벤트에_배정되지_않은_쌍은_excludedUnassigned로_세고_놓침에_넣지_않는다() {
        long a = alert(T.minusHours(2), sigungu1, vec(1, 0));
        long b = alert(T, sigungu1, vec(0.9, 0.1));
        map(event(false, false), a, 1);
        // b 는 event_alert_mapping 에 없다
        label(a, b, EvaluationLabelType.SAME);

        PairwiseReport report = service.evaluate(dataset);

        assertEquals(1, report.excludedUnassigned());
        assertEquals(0, report.missed());
        assertEquals(0, report.merged());
    }

    /**
     * 놓친 쌍에는 원인 대신 사실이 붙는다: 유형("A/B"), 시군구 공유, 발송 간격(시간), 본문 거리.
     * Red: 평가기 뼈대가 빈 목록을 돌려줘 missedPairs 크기 단언에서 실패한다.
     */
    @Test
    void 놓친_쌍에는_유형_시군구_간격_본문_거리가_사실로_채워진다() {
        // 준비: a 는 3시간 전 호우(시군구1, 벡터 x축), b 는 산불(시군구2, 벡터 y축) -> 서로 직교라 코사인 거리 1.0
        long a = alertOfType("호우", T.minusHours(3), sigungu1, vec(1, 0));
        long b = alertOfType("산불", T, sigungu2, vec(0, 1));
        map(event(false, false), a, 1);
        map(event(false, false), b, 1);
        label(a, b, EvaluationLabelType.SAME);

        PairwiseReport report = service.evaluate(dataset);

        assertEquals(1, report.missedPairs().size());
        ErrorPair pair = report.missedPairs().get(0);
        assertEquals(a, pair.alertAId());
        assertEquals(b, pair.alertBId());
        PairFacts facts = pair.facts();
        assertNotNull(facts);
        assertEquals("호우/산불", facts.types());   // 유형이 다르면 라벨의 a/b 순서로 "A/B"
        assertEquals(false, facts.sameSigungu());     // 앞 5자리가 다름
        assertEquals(3L, facts.gapHours());           // T-3h ~ T
        assertEquals(1.0, facts.textDistance(), 1e-3);
    }

    // ---------------------------------------------------------------- 픽스처 헬퍼

    private String anyDistrictCodeOfSigungu(String prefix) {
        return jdbc.queryForObject(
                "select code from legal_district where left(code, 5) = ? order by code limit 1",
                String.class, prefix);
    }

    private long alert(LocalDateTime createdAt, String districtCode, String vector) {
        return alertOfType("테스트", createdAt, districtCode, vector);
    }

    private long alertOfType(String disasterType, LocalDateTime createdAt, String districtCode, String vector) {
        long id = ID.incrementAndGet();
        alertIds.add(id);
        jdbc.update("""
                insert into disaster_alert (disaster_alert_id, sn, message, created_at, emergency_level,
                                            disaster_type, modified_date, original_region, embedding)
                values (?, ?, '평가 서비스 테스트', ?, 'LEVEL_1', ?, ?, '테스트', cast(? as vector))
                """, id, id, Timestamp.valueOf(createdAt), disasterType, Timestamp.valueOf(createdAt), vector);
        if (districtCode != null) {
            jdbc.update("insert into disaster_alert_region (disaster_alert_id, legal_district_code) values (?, ?)",
                    id, districtCode);
        }
        return id;
    }

    private long event(boolean broadcast, boolean advisory) {
        long id = ID.incrementAndGet();
        eventIds.add(id);
        jdbc.update("""
                insert into disaster_events (id, event_title, first_alert_at, last_alert_at, alert_count,
                                             cooldown_hours, is_broadcast, is_advisory)
                values (?, '평가 서비스 테스트', ?, ?, 1, 72, ?, ?)
                """, id, Timestamp.valueOf(T.minusDays(30)), Timestamp.valueOf(T.plusDays(30)), broadcast, advisory);
        return id;
    }

    private void map(long eventId, long alertId, int sequenceNo) {
        jdbc.update("insert into event_alert_mapping (event_id, alert_id, sequence_no) values (?, ?, ?)",
                eventId, alertId, sequenceNo);
    }

    // stratum 컬럼은 CSV 형식 호환 때문에 남아 있어 값을 채우기만 한다 (평가에는 쓰지 않는다).
    private void label(long alertAId, long alertBId, EvaluationLabelType label) {
        jdbc.update("""
                insert into evaluation_label (dataset, alert_a_id, alert_b_id, label, stratum, sub_type)
                values (?, ?, ?, ?, 'GENERAL', 'test')
                """, dataset, alertAId, alertBId, label.name());
    }

    /** 앞 몇 성분만 값이 있고 나머지는 0 인 1536 차원 pgvector 리터럴. 임베딩이 없는 알림은 호출 측에서 null 을 넘긴다. */
    private static String vec(double... head) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < DIM; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(i < head.length ? head[i] : 0);
        }
        return sb.append(']').toString();
    }
}
