package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 라벨 CSV 적재 정책 "같은 데이터셋은 지우고 다시 넣는다"를 실제 DB 로 검증한다.
 *
 * <p>롤백 동작(파싱·FK 실패 시 기존 행 보존)을 가리지 않도록 클래스에 {@code @Transactional} 을 붙이지 않고,
 * 대신 테스트 전용 dataset 이름과 직접 만든 disaster_alert 행만 매 테스트 전후로 정리한다.
 */
@IntegrationTest
class EvaluationLabelLoaderTest {

    private static final String HEADER = "alert_a_id,alert_b_id,label,stratum,sub_type\n";
    private static final long MISSING_ALERT_ID = 9_000_000_000_000L;
    private static final AtomicLong SN = new AtomicLong(980_000_000_000L + System.currentTimeMillis() % 1_000_000L * 1000);

    @Autowired
    private EvaluationLabelLoader loader;
    @Autowired
    private EvaluationLabelRepository repository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DisasterAlertRepository disasterAlertRepository;

    private final List<Long> alertIds = new ArrayList<>();
    private final List<String> datasets = new ArrayList<>();
    private String dataset;
    private long a1, a2, a3, a4;

    @BeforeEach
    void 준비() {
        dataset = newDataset();
        a1 = saveAlert();
        a2 = saveAlert();
        a3 = saveAlert();
        a4 = saveAlert();
    }

    @AfterEach
    void 정리() {
        // @Modifying 쿼리는 트랜잭션이 필요하므로 정리는 JdbcTemplate 으로 한다 (라벨 먼저, 알림 나중 — FK).
        datasets.forEach(d -> jdbc.update("delete from evaluation_label where dataset = ?", d));
        alertIds.forEach(id -> jdbc.update("delete from disaster_alert where disaster_alert_id = ?", id));
    }

    @Test
    void 행_수만큼_저장하고_저장_건수를_반환하며_필드를_매핑한다_UNSURE도_저장된다() {
        String csv = HEADER
                + a1 + "," + a2 + ",SAME,GENERAL,general_same\n"
                + a2 + "," + a3 + ",DIFF,TRAP,trap_template_advisory\n"
                + a3 + "," + a4 + ",UNSURE,BOUNDARY,boundary_near\n";

        int saved = loader.load(dataset, csv);

        assertEquals(3, saved);
        List<EvaluationLabel> rows = repository.findByDatasetOrderById(dataset);
        assertEquals(3, rows.size());
        assertRow(rows.get(0), a1, a2, EvaluationLabelType.SAME, EvaluationStratum.GENERAL, "general_same");
        assertRow(rows.get(1), a2, a3, EvaluationLabelType.DIFF, EvaluationStratum.TRAP, "trap_template_advisory");
        assertRow(rows.get(2), a3, a4, EvaluationLabelType.UNSURE, EvaluationStratum.BOUNDARY, "boundary_near");
    }

    @Test
    void 같은_CSV로_다시_적재해도_행_수가_그대로다() {
        String csv = HEADER
                + a1 + "," + a2 + ",SAME,GENERAL,general_same\n"
                + a2 + "," + a3 + ",DIFF,GENERAL,general_diff\n";

        loader.load(dataset, csv);
        int second = loader.load(dataset, csv);

        assertEquals(2, second);
        assertEquals(2, repository.countByDataset(dataset));
    }

    @Test
    void 라벨을_정정한_CSV로_다시_적재하면_DB_라벨이_갱신된다() {
        loader.load(dataset, HEADER + a1 + "," + a2 + ",SAME,GENERAL,general_same\n");

        loader.load(dataset, HEADER + a1 + "," + a2 + ",DIFF,GENERAL,general_same\n");

        List<EvaluationLabel> rows = repository.findByDatasetOrderById(dataset);
        assertEquals(1, rows.size());
        assertEquals(EvaluationLabelType.DIFF, rows.get(0).getLabel());
    }

    @Test
    void 쌍을_뺀_CSV로_다시_적재하면_그_쌍이_DB에서_사라진다() {
        loader.load(dataset, HEADER
                + a1 + "," + a2 + ",SAME,GENERAL,general_same\n"
                + a2 + "," + a3 + ",DIFF,GENERAL,general_diff\n");

        loader.load(dataset, HEADER + a1 + "," + a2 + ",SAME,GENERAL,general_same\n");

        List<EvaluationLabel> rows = repository.findByDatasetOrderById(dataset);
        assertEquals(1, rows.size());
        assertEquals(a1, rows.get(0).getAlertAId());
        assertEquals(a2, rows.get(0).getAlertBId());
    }

    @Test
    void 다른_dataset의_행은_건드리지_않는다() {
        String other = newDataset();
        repository.save(EvaluationLabel.of(other, new EvaluationLabelRecord(
                a1, a2, EvaluationLabelType.SAME, EvaluationStratum.GENERAL, "general_same")));

        int saved = loader.load(dataset, HEADER + a1 + "," + a2 + ",DIFF,TRAP,trap_x\n");

        assertEquals(1, saved);
        assertEquals(1, repository.countByDataset(dataset), "대상 dataset 에는 적재되어야 한다");
        List<EvaluationLabel> otherRows = repository.findByDatasetOrderById(other);
        assertEquals(1, otherRows.size());
        assertEquals(EvaluationLabelType.SAME, otherRows.get(0).getLabel());
    }

    @Test
    void 잘못된_CSV면_예외가_전파되고_기존_행이_그대로_남는다() {
        seedExisting();

        assertThrows(IllegalArgumentException.class,
                () -> loader.load(dataset, HEADER + a1 + "," + a2 + ",MAYBE,GENERAL,general_same\n"));

        assertExistingIntact();
    }

    @Test
    void 존재하지_않는_알림_id가_있으면_예외가_전파되고_롤백되어_기존_행이_그대로_남는다() {
        seedExisting();
        String csv = HEADER
                + a3 + "," + a4 + ",DIFF,GENERAL,general_diff\n"
                + a1 + "," + MISSING_ALERT_ID + ",SAME,GENERAL,general_same\n";

        assertThrows(DataAccessException.class, () -> loader.load(dataset, csv));

        assertExistingIntact();
    }

    private void seedExisting() {
        repository.save(EvaluationLabel.of(dataset, new EvaluationLabelRecord(
                a1, a2, EvaluationLabelType.SAME, EvaluationStratum.GENERAL, "general_same")));
    }

    private void assertExistingIntact() {
        List<EvaluationLabel> rows = repository.findByDatasetOrderById(dataset);
        assertEquals(1, rows.size());
        assertRow(rows.get(0), a1, a2, EvaluationLabelType.SAME, EvaluationStratum.GENERAL, "general_same");
    }

    private static void assertRow(EvaluationLabel row, long a, long b, EvaluationLabelType label,
                                  EvaluationStratum stratum, String subType) {
        assertEquals(a, row.getAlertAId());
        assertEquals(b, row.getAlertBId());
        assertEquals(label, row.getLabel());
        assertEquals(stratum, row.getStratum());
        assertEquals(subType, row.getSubType());
        assertTrue(row.getCreatedAt() != null, "created_at 이 채워져야 한다");
    }

    private String newDataset() {
        String name = "test-dataset-" + UUID.randomUUID().toString().substring(0, 8);
        datasets.add(name);
        return name;
    }

    private long saveAlert() {
        LocalDateTime now = LocalDateTime.of(1999, 1, 1, 9, 0);
        DisasterAlert alert = disasterAlertRepository.save(DisasterAlert.builder()
                .sn(SN.incrementAndGet())
                .message("평가 라벨 적재 테스트")
                .createdAt(now)
                .emergencyLevel(DisasterLevel.LEVEL_1)
                .disasterType("테스트")
                .modifiedDate(now)
                .originalRegion("테스트")
                .build());
        alertIds.add(alert.getId());
        return alert.getId();
    }
}
