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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 운영 사본 DB 의 기존 이벤트 배정을 라벨과 비교하는 {@link EventEvaluationService} 를 실제 DB 로 검증한다.
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

    // ---------------------------------------------------------------- 예측(predictedSame) 집계

    @Test
    void 두_알림이_같은_이벤트면_SAME_라벨_쌍이_tp로_집계된다() {
        long e = event(false, false);
        long a = alert(T.minusHours(2), sigungu1, vec(1, 0));
        long b = alert(T, sigungu1, vec(0.9, 0.1));
        map(e, a, 1);
        map(e, b, 2);
        label(a, b, EvaluationLabelType.SAME, EvaluationStratum.GENERAL);

        PairwiseReport report = service.evaluate(dataset);

        assertConfusion(report.overall(), 1, 0, 0, 0);
        assertTrue(report.errors().isEmpty());
    }

    @Test
    void 서로_다른_이벤트이고_DIFF_라벨이면_tn으로_집계된다() {
        long a = alert(T.minusHours(2), sigungu1, vec(1, 0));
        long b = alert(T, sigungu1, vec(0.9, 0.1));
        map(event(false, false), a, 1);
        map(event(false, false), b, 1);
        label(a, b, EvaluationLabelType.DIFF, EvaluationStratum.GENERAL);

        PairwiseReport report = service.evaluate(dataset);

        assertConfusion(report.overall(), 0, 0, 0, 1);
        assertTrue(report.errors().isEmpty());
    }

    @Test
    void 서로_다른_이벤트이고_SAME_라벨이면_fn으로_집계된다() {
        long a = alert(T.minusHours(2), sigungu1, vec(1, 0));
        long b = alert(T, sigungu1, vec(0.9, 0.1));
        map(event(false, false), a, 1);
        map(event(false, false), b, 1);
        label(a, b, EvaluationLabelType.SAME, EvaluationStratum.GENERAL);

        PairwiseReport report = service.evaluate(dataset);

        assertConfusion(report.overall(), 0, 0, 1, 0);
        assertEquals(1, report.errors().size());
    }

    @Test
    void 같은_이벤트인데_DIFF_라벨이면_fp이고_OVER_MERGE다() {
        long e = event(false, false);
        long a = alert(T.minusHours(2), sigungu1, vec(1, 0));
        long b = alert(T, sigungu1, vec(0.9, 0.1));
        map(e, a, 1);
        map(e, b, 2);
        label(a, b, EvaluationLabelType.DIFF, EvaluationStratum.TRAP);

        PairwiseReport report = service.evaluate(dataset);

        assertConfusion(report.overall(), 0, 1, 0, 0);
        assertSingleError(report, a, b, EvaluationStratum.TRAP, ErrorCause.OVER_MERGE);
    }

    @Test
    void 한쪽_알림이_매핑에_없으면_예측은_false이고_SAME이면_UNKNOWN_MISS다() {
        long a = alert(T.minusHours(2), sigungu1, vec(1, 0));
        long b = alert(T, sigungu1, vec(0.9, 0.1));
        map(event(false, false), a, 1);
        // b 는 event_alert_mapping 에 없다
        label(a, b, EvaluationLabelType.SAME, EvaluationStratum.GENERAL);

        PairwiseReport report = service.evaluate(dataset);

        assertConfusion(report.overall(), 0, 0, 1, 0);
        assertSingleError(report, a, b, EvaluationStratum.GENERAL, ErrorCause.UNKNOWN_MISS);
    }

    @Test
    void 임베딩이_없는_알림이_있으면_후보_계산_불가로_UNKNOWN_MISS다() {
        long a = alert(T.minusHours(2), sigungu1, vec(1, 0));
        long b = alert(T, sigungu1, null);
        map(event(false, false), a, 1);
        map(event(false, false), b, 1);
        label(a, b, EvaluationLabelType.SAME, EvaluationStratum.GENERAL);

        PairwiseReport report = service.evaluate(dataset);

        assertSingleError(report, a, b, EvaluationStratum.GENERAL, ErrorCause.UNKNOWN_MISS);
    }

    // ---------------------------------------------------------------- 후보 재현: DECISION_MISS / RETRIEVAL_MISS

    @Test
    void fn이고_a의_이벤트가_b_시점_후보_안에_있으면_DECISION_MISS다_라벨_순서가_뒤바뀌어도_시각으로_판단한다() {
        long a = alert(T.minusHours(2), sigungu1, vec(1, 0));
        long b = alert(T, sigungu1, vec(0.9, 0.1));
        map(event(false, false), a, 1);
        map(event(false, false), b, 1);
        // 라벨에는 나중 알림(b)이 alert_a_id 로 적혀 있다
        label(b, a, EvaluationLabelType.SAME, EvaluationStratum.BOUNDARY);

        PairwiseReport report = service.evaluate(dataset);

        assertSingleError(report, b, a, EvaluationStratum.BOUNDARY, ErrorCause.DECISION_MISS);
    }

    @Test
    void 시군구가_다르면_후보가_못_돼_RETRIEVAL_MISS다() {
        long a = alert(T.minusHours(2), sigungu2, vec(1, 0));
        long b = alert(T, sigungu1, vec(0.9, 0.1));
        map(event(false, false), a, 1);
        map(event(false, false), b, 1);
        label(a, b, EvaluationLabelType.SAME, EvaluationStratum.GENERAL);

        PairwiseReport report = service.evaluate(dataset);

        assertSingleError(report, a, b, EvaluationStratum.GENERAL, ErrorCause.RETRIEVAL_MISS);
    }

    @Test
    void b보다_168시간_넘게_이른_이벤트는_후보가_못_돼_RETRIEVAL_MISS다() {
        long a = alert(T.minusHours(200), sigungu1, vec(1, 0));
        long b = alert(T, sigungu1, vec(0.9, 0.1));
        map(event(false, false), a, 1);
        map(event(false, false), b, 1);
        label(a, b, EvaluationLabelType.SAME, EvaluationStratum.GENERAL);

        PairwiseReport report = service.evaluate(dataset);

        assertSingleError(report, a, b, EvaluationStratum.GENERAL, ErrorCause.RETRIEVAL_MISS);
    }

    @Test
    void 시점_복원_b보다_나중에_생긴_알림_때문에_후보가_되지_않는다() {
        long e = event(false, false);
        long a = alert(T.minusHours(200), sigungu1, vec(1, 0));      // b 이전 알림은 윈도우 밖
        long later = alert(T.plusHours(5), sigungu1, vec(1, 0));     // b 이후 알림 — 현재 상태로는 윈도우 안
        long b = alert(T, sigungu1, vec(0.9, 0.1));
        map(e, a, 1);
        map(e, later, 2);
        map(event(false, false), b, 1);
        label(a, b, EvaluationLabelType.SAME, EvaluationStratum.GENERAL);

        PairwiseReport report = service.evaluate(dataset);

        assertSingleError(report, a, b, EvaluationStratum.GENERAL, ErrorCause.RETRIEVAL_MISS);
    }

    @Test
    void broadcast_이벤트는_후보에서_제외돼_RETRIEVAL_MISS다() {
        long a = alert(T.minusHours(2), sigungu1, vec(1, 0));
        long b = alert(T, sigungu1, vec(0.9, 0.1));
        map(event(true, false), a, 1);
        map(event(false, false), b, 1);
        label(a, b, EvaluationLabelType.SAME, EvaluationStratum.GENERAL);

        PairwiseReport report = service.evaluate(dataset);

        assertSingleError(report, a, b, EvaluationStratum.GENERAL, ErrorCause.RETRIEVAL_MISS);
    }

    @Test
    void advisory_이벤트는_후보에서_제외돼_RETRIEVAL_MISS다() {
        long a = alert(T.minusHours(2), sigungu1, vec(1, 0));
        long b = alert(T, sigungu1, vec(0.9, 0.1));
        map(event(false, true), a, 1);
        map(event(false, false), b, 1);
        label(a, b, EvaluationLabelType.SAME, EvaluationStratum.GENERAL);

        PairwiseReport report = service.evaluate(dataset);

        assertSingleError(report, a, b, EvaluationStratum.GENERAL, ErrorCause.RETRIEVAL_MISS);
    }

    @Test
    void 더_가까운_다른_이벤트_3개가_있으면_a의_이벤트는_4위로_밀려_RETRIEVAL_MISS다() {
        long a = alert(T.minusHours(2), sigungu1, vec(0.5, 0.5));    // 코사인 거리 약 0.29
        long b = alert(T, sigungu1, vec(1, 0));
        map(event(false, false), a, 1);
        map(event(false, false), b, 1);
        closerEvent(vec(0.99, 0.01));
        closerEvent(vec(0.98, 0.02));
        closerEvent(vec(0.97, 0.03));
        label(a, b, EvaluationLabelType.SAME, EvaluationStratum.GENERAL);

        PairwiseReport report = service.evaluate(dataset);

        assertSingleError(report, a, b, EvaluationStratum.GENERAL, ErrorCause.RETRIEVAL_MISS);
    }

    @Test
    void 더_가까운_다른_이벤트가_2개면_a의_이벤트는_3위로_후보에_남아_DECISION_MISS다() {
        long a = alert(T.minusHours(2), sigungu1, vec(0.5, 0.5));
        long b = alert(T, sigungu1, vec(1, 0));
        map(event(false, false), a, 1);
        map(event(false, false), b, 1);
        closerEvent(vec(0.99, 0.01));
        closerEvent(vec(0.98, 0.02));
        label(a, b, EvaluationLabelType.SAME, EvaluationStratum.GENERAL);

        PairwiseReport report = service.evaluate(dataset);

        assertSingleError(report, a, b, EvaluationStratum.GENERAL, ErrorCause.DECISION_MISS);
    }

    @Test
    void 순위는_알림이_아니라_이벤트별_최소_거리로_매긴다() {
        long a = alert(T.minusHours(2), sigungu1, vec(0.5, 0.5));
        long b = alert(T, sigungu1, vec(1, 0));
        map(event(false, false), a, 1);
        map(event(false, false), b, 1);
        // 매우 가까운 알림 3개를 가진 이벤트 하나 — 알림 단위로 세면 top-3 를 독점해 a 가 밀려난다
        long crowded = event(false, false);
        map(crowded, alert(T.minusHours(3), sigungu1, vec(0.999, 0.001)), 1);
        map(crowded, alert(T.minusHours(3), sigungu1, vec(0.998, 0.002)), 2);
        map(crowded, alert(T.minusHours(3), sigungu1, vec(0.997, 0.003)), 3);
        closerEvent(vec(0.99, 0.01));
        label(a, b, EvaluationLabelType.SAME, EvaluationStratum.GENERAL);

        PairwiseReport report = service.evaluate(dataset);

        assertSingleError(report, a, b, EvaluationStratum.GENERAL, ErrorCause.DECISION_MISS);
    }

    // ---------------------------------------------------------------- UNSURE / 층별 / 빈 dataset

    @Test
    void UNSURE_라벨은_지표에서_빠지고_excludedUnsure로만_센다() {
        long e = event(false, false);
        long a1 = alert(T.minusHours(2), sigungu1, vec(1, 0));
        long b1 = alert(T, sigungu1, vec(0.9, 0.1));
        long a2 = alert(T.minusHours(3), sigungu1, vec(1, 0));
        long b2 = alert(T.minusHours(1), sigungu1, vec(0.9, 0.1));
        map(e, a1, 1);
        map(e, b1, 2);
        map(e, a2, 3);
        map(e, b2, 4);
        label(a1, b1, EvaluationLabelType.SAME, EvaluationStratum.GENERAL);
        label(a2, b2, EvaluationLabelType.UNSURE, EvaluationStratum.GENERAL);

        PairwiseReport report = service.evaluate(dataset);

        assertEquals(1, report.excludedUnsure());
        assertConfusion(report.overall(), 1, 0, 0, 0);
    }

    @Test
    void byStratum은_라벨의_stratum별로_집계한다() {
        long e = event(false, false);
        long g1 = alert(T.minusHours(2), sigungu1, vec(1, 0));
        long g2 = alert(T, sigungu1, vec(0.9, 0.1));
        map(e, g1, 1);
        map(e, g2, 2);
        long t1 = alert(T.minusHours(2), sigungu1, vec(1, 0));
        long t2 = alert(T, sigungu1, vec(0.9, 0.1));
        map(event(false, false), t1, 1);
        map(event(false, false), t2, 1);
        long b1 = alert(T.minusHours(2), sigungu1, vec(1, 0));
        long b2 = alert(T, sigungu1, vec(0.9, 0.1));
        map(event(false, false), b1, 1);   // b2 는 매핑 없음 -> fn
        label(g1, g2, EvaluationLabelType.SAME, EvaluationStratum.GENERAL);
        label(t1, t2, EvaluationLabelType.DIFF, EvaluationStratum.TRAP);
        label(b1, b2, EvaluationLabelType.SAME, EvaluationStratum.BOUNDARY);

        PairwiseReport report = service.evaluate(dataset);

        assertConfusion(report.overall(), 1, 0, 1, 1);
        assertConfusion(report.byStratum().get(EvaluationStratum.GENERAL), 1, 0, 0, 0);
        assertConfusion(report.byStratum().get(EvaluationStratum.TRAP), 0, 0, 0, 1);
        assertConfusion(report.byStratum().get(EvaluationStratum.BOUNDARY), 0, 0, 1, 0);
    }

    /** 참고: 뼈대(빈 report)도 이 기대를 만족하므로 Red 단계에서 우연히 통과한다 — 회귀 방지용. */
    @Test
    void 라벨이_없는_dataset은_빈_report다() {
        PairwiseReport report = service.evaluate(dataset);

        assertConfusion(report.overall(), 0, 0, 0, 0);
        assertTrue(report.byStratum().isEmpty());
        assertTrue(report.errors().isEmpty());
        assertEquals(0, report.excludedUnsure());
    }

    // ---------------------------------------------------------------- 단언 헬퍼

    private static void assertConfusion(Confusion c, int tp, int fp, int fn, int tn) {
        assertEquals(List.of(tp, fp, fn, tn), List.of(c.tp(), c.fp(), c.fn(), c.tn()),
                "혼동 행렬 (tp, fp, fn, tn)");
    }

    private static void assertSingleError(PairwiseReport report, long a, long b,
                                          EvaluationStratum stratum, ErrorCause cause) {
        assertEquals(List.of(new ErrorCase(a, b, stratum, cause)), report.errors());
    }

    // ---------------------------------------------------------------- 픽스처 헬퍼

    private String anyDistrictCodeOfSigungu(String prefix) {
        return jdbc.queryForObject(
                "select code from legal_district where left(code, 5) = ? order by code limit 1",
                String.class, prefix);
    }

    /** b 와 같은 시군구에서 b 직전에 생긴, 알림 1개짜리 별도 이벤트. */
    private void closerEvent(String vector) {
        map(event(false, false), alert(T.minusHours(1), sigungu1, vector), 1);
    }

    private long alert(LocalDateTime createdAt, String districtCode, String vector) {
        long id = ID.incrementAndGet();
        alertIds.add(id);
        jdbc.update("""
                insert into disaster_alert (disaster_alert_id, sn, message, created_at, emergency_level,
                                            disaster_type, modified_date, original_region, embedding)
                values (?, ?, '평가 서비스 테스트', ?, 'LEVEL_1', '테스트', ?, '테스트', cast(? as vector))
                """, id, id, Timestamp.valueOf(createdAt), Timestamp.valueOf(createdAt), vector);
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

    private void label(long alertAId, long alertBId, EvaluationLabelType label, EvaluationStratum stratum) {
        jdbc.update("""
                insert into evaluation_label (dataset, alert_a_id, alert_b_id, label, stratum, sub_type)
                values (?, ?, ?, ?, ?, 'test')
                """, dataset, alertAId, alertBId, label.name(), stratum.name());
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
