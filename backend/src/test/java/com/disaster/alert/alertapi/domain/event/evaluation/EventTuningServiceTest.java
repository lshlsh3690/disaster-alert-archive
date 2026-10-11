package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.model.ThresholdSweep;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 임계값 스윕 {@link EventTuningService} 를 실제 DB 로 검증한다. 쌍마다 "유효 거리"(이미 같은 이벤트 0.0,
 * a 의 이벤트가 b 시점 top-1 후보면 그 최소 거리, 그 외 무한대)를 만들어 t 별 tp/fp/fn 을 센다.
 *
 * <p>{@link EventEvaluationServiceTest} 와 같은 방식: 클래스에 {@code @Transactional} 없이 직접 INSERT 하고
 * 테스트 후 JdbcTemplate 으로 정리한다. 거리 경계(t = 거리)는 pgvector 의 float 오차를 피하려고
 * DB 가 계산한 값을 그대로 임계값으로 쓴다.
 */
@IntegrationTest
class EventTuningServiceTest {

    private static final LocalDateTime T = LocalDateTime.of(2020, 3, 10, 12, 0);
    private static final int DIM = 1536;
    private static final AtomicLong ID = new AtomicLong(970_000_000_000L + System.currentTimeMillis() % 1_000_000L * 1000);

    @Autowired
    private EventTuningService service;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> alertIds = new ArrayList<>();
    private final List<Long> eventIds = new ArrayList<>();
    private final List<String> datasets = new ArrayList<>();
    private String dataset;
    private String sigungu1;

    @BeforeEach
    void 준비() {
        dataset = "test-tune-" + UUID.randomUUID().toString().substring(0, 8);
        datasets.add(dataset);
        sigungu1 = jdbc.queryForObject(
                "select code from legal_district where left(code, 5) = '11110' order by code limit 1", String.class);
    }

    @AfterEach
    void 정리() {
        datasets.forEach(d -> jdbc.update("delete from evaluation_label where dataset = ?", d));
        alertIds.forEach(id -> {
            jdbc.update("delete from event_alert_mapping where alert_id = ?", id);
            jdbc.update("delete from disaster_alert_region where disaster_alert_id = ?", id);
        });
        eventIds.forEach(id -> jdbc.update("delete from disaster_events where id = ?", id));
        alertIds.forEach(id -> jdbc.update("delete from disaster_alert where disaster_alert_id = ?", id));
    }

    @Test
    void 이미_같은_이벤트인_SAME_쌍은_모든_임계값에서_tp다() {
        long e = event();
        long a = alert(T.minusHours(2), vec(1, 0));
        long b = alert(T, vec(0, 1));          // 거리가 멀어도 이미 병합돼 있다
        map(e, a, 1);
        map(e, b, 2);
        label(a, b, EvaluationLabelType.SAME, EvaluationStratum.GENERAL);

        TuningReport report = service.sweep(dataset, List.of(0.05, 0.15, 0.40));

        assertEquals(3, report.overall().size());
        report.overall().forEach(r -> assertRow(r, 1, 0, 0));
    }

    @Test
    void 병합_안_된_SAME_쌍은_a의_이벤트가_top1이면_거리_이상의_임계값에서_tp다_경계_포함() {
        long a = alert(T.minusHours(2), vec(0.8, 0.6));   // b 와 코사인 거리 약 0.20
        long b = alert(T, vec(1, 0));
        map(event(), a, 1);
        map(event(), b, 1);
        label(a, b, EvaluationLabelType.SAME, EvaluationStratum.GENERAL);
        double d = distance(a, b);

        TuningReport report = service.sweep(dataset, List.of(0.15, d, 0.40));

        assertEquals(3, report.overall().size());
        assertRow(report.overall().get(0), 0, 0, 1);
        assertRow(report.overall().get(1), 1, 0, 0);
        assertRow(report.overall().get(2), 1, 0, 0);
        assertTrue(report.overall().get(2).recall() > report.overall().get(0).recall());
    }

    @Test
    void 병합_안_된_SAME_쌍인데_a의_이벤트가_top1이_아니면_모든_임계값에서_fn이다() {
        long a = alert(T.minusHours(2), vec(0.5, 0.5));   // 거리 약 0.29
        long b = alert(T, vec(1, 0));
        map(event(), a, 1);
        map(event(), b, 1);
        map(event(), alert(T.minusHours(1), vec(0.99, 0.01)), 1);   // 더 가까운 다른 이벤트
        label(a, b, EvaluationLabelType.SAME, EvaluationStratum.GENERAL);

        TuningReport report = service.sweep(dataset, List.of(0.05, 0.15, 0.40, 0.90));

        assertEquals(4, report.overall().size());
        report.overall().forEach(r -> assertRow(r, 0, 0, 1));
    }

    @Test
    void DIFF_쌍은_a의_이벤트가_top1이면_거리_이상의_임계값에서_fp다() {
        long a = alert(T.minusHours(2), vec(0.82, 0.57));   // b 와 코사인 거리 약 0.18
        long b = alert(T, vec(1, 0));
        map(event(), a, 1);
        map(event(), b, 1);
        label(a, b, EvaluationLabelType.DIFF, EvaluationStratum.TRAP);
        double d = distance(a, b);

        TuningReport report = service.sweep(dataset, List.of(0.10, d, 0.40));

        assertEquals(3, report.overall().size());
        assertRow(report.overall().get(0), 0, 0, 0);   // tn
        assertRow(report.overall().get(1), 0, 1, 0);
        assertRow(report.overall().get(2), 0, 1, 0);
    }

    @Test
    void 임베딩이나_이벤트_매핑이_없는_쌍은_어떤_임계값에서도_병합되지_않는다() {
        long a1 = alert(T.minusHours(2), vec(1, 0));
        long b1 = alert(T, null);                          // b 임베딩 없음
        map(event(), a1, 1);
        map(event(), b1, 1);
        long a2 = alert(T.minusHours(2), vec(1, 0));
        long b2 = alert(T, vec(1, 0));                     // b2 는 매핑 없음
        map(event(), a2, 1);
        long a3 = alert(T.minusHours(2), vec(1, 0));
        long b3 = alert(T, vec(1, 0));                     // DIFF 이고 b3 매핑 없음
        map(event(), a3, 1);
        label(a1, b1, EvaluationLabelType.SAME, EvaluationStratum.GENERAL);
        label(a2, b2, EvaluationLabelType.SAME, EvaluationStratum.GENERAL);
        label(a3, b3, EvaluationLabelType.DIFF, EvaluationStratum.GENERAL);

        TuningReport report = service.sweep(dataset, List.of(0.05, 0.40, 1.50));

        assertEquals(3, report.overall().size());
        report.overall().forEach(r -> assertRow(r, 0, 0, 2));
    }

    @Test
    void UNSURE_라벨은_곡선에서_제외된다() {
        long e = event();
        long a1 = alert(T.minusHours(2), vec(1, 0));
        long b1 = alert(T, vec(1, 0));
        map(e, a1, 1);
        map(e, b1, 2);
        label(a1, b1, EvaluationLabelType.SAME, EvaluationStratum.GENERAL);
        // 포함됐다면 t>=0.18 에서 fp 가 될 UNSURE 쌍
        long a2 = alert(T.minusHours(2), vec(0.82, 0.57));
        long b2 = alert(T.plusHours(1), vec(1, 0));
        map(event(), a2, 1);
        map(event(), b2, 1);
        label(a2, b2, EvaluationLabelType.UNSURE, EvaluationStratum.GENERAL);

        TuningReport report = service.sweep(dataset, List.of(0.05, 0.40));

        assertEquals(2, report.overall().size());
        report.overall().forEach(r -> assertRow(r, 1, 0, 0));
        assertEquals(List.of(EvaluationStratum.GENERAL), new ArrayList<>(report.byStratum().keySet()));
    }

    @Test
    void byStratum은_층별_독립_곡선이고_키는_쌍이_있는_층만_GENERAL_BOUNDARY_TRAP_순서다() {
        // TRAP: DIFF, top-1 거리 약 0.18 -> t=0.40 에서 fp
        long ta = alert(T.minusHours(2), vec(0.82, 0.57));
        long tb = alert(T, vec(1, 0));
        map(event(), ta, 1);
        map(event(), tb, 1);
        label(ta, tb, EvaluationLabelType.DIFF, EvaluationStratum.TRAP);
        // BOUNDARY: SAME, top-1 거리 약 0.20 -> t=0.40 에서 tp
        // 같은 시군구의 세 쌍이 서로의 후보(top-1)가 되지 않도록 벡터 방향을 분리한다:
        // TRAP 은 x축 근처, BOUNDARY 는 y축 근처, GENERAL 은 z축(나머지와 직교, 거리 1.0).
        long ba = alert(T.minusHours(2), vec(0.6, 0.8));
        long bb = alert(T, vec(0, 1));
        map(event(), ba, 1);
        map(event(), bb, 1);
        label(ba, bb, EvaluationLabelType.SAME, EvaluationStratum.BOUNDARY);
        // GENERAL: 이미 같은 이벤트 SAME -> 항상 tp
        long e = event();
        long ga = alert(T.minusHours(2), vec(0, 0, 1));
        long gb = alert(T, vec(0, 0, 1));
        map(e, ga, 1);
        map(e, gb, 2);
        label(ga, gb, EvaluationLabelType.SAME, EvaluationStratum.GENERAL);

        TuningReport report = service.sweep(dataset, List.of(0.05, 0.40));

        assertEquals(List.of(EvaluationStratum.GENERAL, EvaluationStratum.BOUNDARY, EvaluationStratum.TRAP),
                new ArrayList<>(report.byStratum().keySet()));
        assertRow(report.overall().get(0), 1, 0, 1);
        assertRow(report.overall().get(1), 2, 1, 0);
        assertRow(report.byStratum().get(EvaluationStratum.GENERAL).get(1), 1, 0, 0);
        assertRow(report.byStratum().get(EvaluationStratum.BOUNDARY).get(0), 0, 0, 1);
        assertRow(report.byStratum().get(EvaluationStratum.BOUNDARY).get(1), 1, 0, 0);
        assertRow(report.byStratum().get(EvaluationStratum.TRAP).get(0), 0, 0, 0);
        assertRow(report.byStratum().get(EvaluationStratum.TRAP).get(1), 0, 1, 0);
    }

    @Test
    void 곡선은_입력_임계값_순서를_유지한다() {
        long e = event();
        long a = alert(T.minusHours(2), vec(1, 0));
        long b = alert(T, vec(1, 0));
        map(e, a, 1);
        map(e, b, 2);
        label(a, b, EvaluationLabelType.SAME, EvaluationStratum.GENERAL);

        TuningReport report = service.sweep(dataset, List.of(0.40, 0.05, 0.15));

        assertEquals(List.of(0.40, 0.05, 0.15),
                report.overall().stream().map(ThresholdSweep.Row::maxDistance).toList());
    }

    @Test
    void 임계값이_비면_빈_곡선이다() {
        long e = event();
        long a = alert(T.minusHours(2), vec(1, 0));
        long b = alert(T, vec(1, 0));
        map(e, a, 1);
        map(e, b, 2);
        label(a, b, EvaluationLabelType.SAME, EvaluationStratum.GENERAL);

        TuningReport report = service.sweep(dataset, List.of());

        assertTrue(report.overall().isEmpty());
        assertEquals(List.of(EvaluationStratum.GENERAL), new ArrayList<>(report.byStratum().keySet()));
        assertTrue(report.byStratum().get(EvaluationStratum.GENERAL).isEmpty());
    }

    @Test
    void 라벨이_없는_dataset은_예외_없이_0_결과다() {
        TuningReport report = service.sweep(dataset, List.of(0.10, 0.30));

        assertEquals(2, report.overall().size());
        report.overall().forEach(r -> assertRow(r, 0, 0, 0));
        assertTrue(report.byStratum().isEmpty());
    }

    // ---------------------------------------------------------------- 단언 헬퍼

    private static void assertRow(ThresholdSweep.Row r, int tp, int fp, int fn) {
        assertEquals(List.of(tp, fp, fn), List.of(r.tp(), r.fp(), r.fn()),
                "t=" + r.maxDistance() + " 의 (tp, fp, fn)");
    }

    // ---------------------------------------------------------------- 픽스처 헬퍼

    /** DB 가 계산한 두 알림의 코사인 거리 — 경계 임계값으로 그대로 쓰기 위함. */
    private double distance(long idA, long idB) {
        return jdbc.queryForObject("""
                select x.embedding <=> y.embedding from disaster_alert x, disaster_alert y
                where x.disaster_alert_id = ? and y.disaster_alert_id = ?
                """, Double.class, idA, idB);
    }

    private long alert(LocalDateTime createdAt, String vector) {
        long id = ID.incrementAndGet();
        alertIds.add(id);
        jdbc.update("""
                insert into disaster_alert (disaster_alert_id, sn, message, created_at, emergency_level,
                                            disaster_type, modified_date, original_region, embedding)
                values (?, ?, '튜닝 서비스 테스트', ?, 'LEVEL_1', '테스트', ?, '테스트', cast(? as vector))
                """, id, id, Timestamp.valueOf(createdAt), Timestamp.valueOf(createdAt), vector);
        jdbc.update("insert into disaster_alert_region (disaster_alert_id, legal_district_code) values (?, ?)",
                id, sigungu1);
        return id;
    }

    private long event() {
        long id = ID.incrementAndGet();
        eventIds.add(id);
        jdbc.update("""
                insert into disaster_events (id, event_title, first_alert_at, last_alert_at, alert_count,
                                             cooldown_hours, is_broadcast, is_advisory)
                values (?, '튜닝 서비스 테스트', ?, ?, 1, 72, false, false)
                """, id, Timestamp.valueOf(T.minusDays(30)), Timestamp.valueOf(T.plusDays(30)));
        return id;
    }

    private void map(long eventId, long alertId, int sequenceNo) {
        jdbc.update("insert into event_alert_mapping (event_id, alert_id, sequence_no) values (?, ?, ?)",
                eventId, alertId, sequenceNo);
    }

    private void label(long alertAId, long alertBId, EvaluationLabelType label, EvaluationStratum stratum) {
        jdbc.update("""
                insert into evaluation_label (dataset, alert_a_id, alert_b_id, label, stratum, sub_type)
                values (?, ?, ?, ?, ?, 'test')
                """, dataset, alertAId, alertBId, label.name(), stratum.name());
    }

    /** 앞 몇 성분만 값이 있고 나머지는 0 인 1536 차원 pgvector 리터럴. 임베딩이 없는 알림은 null 을 넘긴다. */
    private static String vec(double... head) {
        if (head == null) {
            return null;
        }
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
