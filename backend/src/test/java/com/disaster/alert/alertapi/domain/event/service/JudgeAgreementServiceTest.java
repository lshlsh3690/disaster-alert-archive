package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.event.service.JudgeAgreementService.VersionAgreement;
import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 판사 일치율을 DB 측정 기록에서 꺼내는 서비스(specs/014). 계산 규칙 자체는 순수 단위 테스트(JudgeAgreementCalculatorTest)가 보고,
 * 여기서는 시드(V146)가 들어갔는지와 서비스가 측정·라벨을 읽어 계산기에 제대로 넘기는지만 본다.
 *
 * <p>테스트 DB 에는 272쌍 라벨이 없고 라벨 표는 알림 외래 키가 있어, 작은 측정과 그 쌍의 알림·라벨을 테스트 트랜잭션 안에 직접
 * 넣는다(끝나면 롤백). 버전 이름은 실제 버전과 겹치지 않게 테스트 전용 이름을 쓴다. id 는 실제 데이터와 겹치지 않게 큰 값이다.
 */
@IntegrationTest
@Transactional
class JudgeAgreementServiceTest {

    private static final String VERSION = "v-test";
    private static final String DATASET = "judge-agreement-service-test";

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JudgeAgreementService service;
    @Autowired
    EntityManager entityManager;

    // 결정: 코드 상수에 있던 v1·v2 일치율이 시드로 그대로 옮겨져야 한다. v2 는 11-18 리포트에서 복원한 쌍별 판정 225건
    // (판정 분포 SAME 134 · DIFF 88 · UNSURE 3), v1 은 쌍별 판정 없이 합계 124/142 · 77/80 이다.
    @Test
    void 시드에_v1_합계와_v2_쌍별_판정_225건이_들어_있다() {
        // 실행: 시드 행을 읽는다
        Map<String, Object> v1 = jdbc.queryForMap("SELECT * FROM judge_agreement_run WHERE judge_version = 'v1' AND origin = 'LEGACY_IMPORT'");
        Map<String, Object> distribution = jdbc.queryForMap(
                "SELECT count(*) AS total, count(*) FILTER (WHERE verdict = 'SAME') AS same,"
                        + " count(*) FILTER (WHERE verdict = 'DIFF') AS diff, count(*) FILTER (WHERE verdict = 'UNSURE') AS unsure"
                        + " FROM judge_agreement_run_pair p JOIN judge_agreement_run r ON r.id = p.run_id"
                        + " WHERE r.judge_version = 'v2' AND r.origin = 'LEGACY_IMPORT'");

        // 검증: v1 은 합계만 있고 쌍별 판정이 없다
        assertThat(v1.get("has_pair_verdicts")).isEqualTo(false);
        assertThat(v1.get("legacy_same_num")).isEqualTo(124);
        assertThat(v1.get("legacy_same_den")).isEqualTo(142);
        assertThat(v1.get("legacy_diff_num")).isEqualTo(77);
        assertThat(v1.get("legacy_diff_den")).isEqualTo(80);
        // 검증: v2 쌍별 판정 225건과 판정 분포
        assertThat(distribution.get("total")).isEqualTo(225L);
        assertThat(distribution.get("same")).isEqualTo(134L);
        assertThat(distribution.get("diff")).isEqualTo(88L);
        assertThat(distribution.get("unsure")).isEqualTo(3L);
    }

    // 결정: 일치율은 그 버전의 가장 늦은 측정의 쌍별 판정 × 현재 라벨로 센다. 그래서 라벨을 고치면 판사를 다시 부르지 않고도
    // 다음 조회부터 값이 바뀐다(SC-003).
    @Test
    void 최신_측정의_판정과_현재_라벨로_세고_라벨을_고치면_값이_바뀐다() {
        // 준비: 알림 6개로 쌍 3개. 판정은 ①SAME ②DIFF ③SAME, 라벨은 ①SAME ②SAME ③DIFF
        for (long id = 9_300_000_001L; id <= 9_300_000_006L; id++) {
            insertAlert(id);
        }
        long run = insertRun("2099-01-02 00:00:00");
        insertVerdict(run, 9_300_000_001L, 9_300_000_002L, "SAME");
        insertVerdict(run, 9_300_000_003L, 9_300_000_004L, "DIFF");
        insertVerdict(run, 9_300_000_005L, 9_300_000_006L, "SAME");
        insertLabel(9_300_000_001L, 9_300_000_002L, "SAME");
        // 라벨 쪽은 알림 순서를 반대로 저장해 둔다 — 순서와 상관없이 같은 쌍으로 맞춰야 한다
        insertLabel(9_300_000_004L, 9_300_000_003L, "SAME");
        insertLabel(9_300_000_005L, 9_300_000_006L, "DIFF");

        // 실행 1
        VersionAgreement before = service.latest(VERSION).orElseThrow();

        // 검증 1: 같은 사건 1/2(① 맞음, ② 틀림), 다른 사건 0/1(③ 틀림), 측정일, 다시 계산되는 값
        assertThat(before.agreement().sameNum()).isEqualTo(1);
        assertThat(before.agreement().sameDen()).isEqualTo(2);
        assertThat(before.agreement().diffNum()).isZero();
        assertThat(before.agreement().diffDen()).isEqualTo(1);
        assertThat(before.measuredOn()).isEqualTo(LocalDate.of(2099, 1, 2));
        assertThat(before.recomputable()).isTrue();
        assertThat(before.unmeasuredPairs()).isZero();

        // 준비 2: 관리자가 ② 의 라벨을 SAME → DIFF 로 고친다(판사 판정 DIFF 와 이제 맞음)
        jdbc.update("UPDATE evaluation_pair_label SET label = 'DIFF' WHERE dataset = ? AND alert_a_id = ?", DATASET, 9_300_000_004L);
        // 테스트 트랜잭션 하나에서 두 번 조회하므로 첫 조회 때 읽은 라벨 엔티티가 남아 있다 — 운영에서는 조회마다 트랜잭션이 새로
        // 열리는 것과 같게, JDBC 로 바꾼 값을 다시 읽도록 비운다
        entityManager.clear();

        // 실행 2
        VersionAgreement after = service.latest(VERSION).orElseThrow();

        // 검증 2: 같은 사건 1/1, 다른 사건 1/2(② 맞음, ③ 틀림)
        assertThat(after.agreement().sameNum()).isEqualTo(1);
        assertThat(after.agreement().sameDen()).isEqualTo(1);
        assertThat(after.agreement().diffNum()).isEqualTo(1);
        assertThat(after.agreement().diffDen()).isEqualTo(2);
    }

    // 결정: 쌍별 판정이 있는 측정인데 그 셋의 라벨이 이 DB 에 하나도 없으면 셀 정답이 없다 — 비워서 돌려주고, 일일 채점은
    // 판사를 부르지 않고 건너뛴다(운영에 라벨 덤프를 넣기 전이 이 상태다).
    @Test
    void 셋의_라벨이_하나도_없으면_셀_수_없어_비어_있다() {
        // 준비: 측정과 판정만 있고 라벨은 없다
        long run = insertRun("2099-01-03 00:00:00");
        insertVerdict(run, 9_300_000_101L, 9_300_000_102L, "SAME");

        // 실행
        Optional<VersionAgreement> result = service.latest(VERSION);

        // 검증
        assertThat(result).isEmpty();
    }

    private void insertAlert(long id) {
        jdbc.update("INSERT INTO disaster_alert(disaster_alert_id, sn, message, created_at, disaster_type, alert_kind) "
                + "VALUES (?, ?, '테스트 본문', '2001-03-04 09:00:00', '화재', 'INCIDENT')", id, id);
    }

    private long insertRun(String finishedAt) {
        return jdbc.queryForObject("INSERT INTO judge_agreement_run(judge_version, model, dataset, origin, finished_at,"
                + " prompt_tokens, completion_tokens, estimated_usd, has_pair_verdicts)"
                + " VALUES (?, 'test-model', ?, 'CLI', CAST(? AS timestamp), 0, 0, 0, true) RETURNING id",
                Long.class, VERSION, DATASET, finishedAt);
    }

    private void insertVerdict(long runId, long a, long b, String verdict) {
        jdbc.update("INSERT INTO judge_agreement_run_pair(run_id, alert_a, alert_b, verdict) VALUES (?, ?, ?, ?)",
                runId, Math.min(a, b), Math.max(a, b), verdict);
    }

    private void insertLabel(long a, long b, String label) {
        jdbc.update("INSERT INTO evaluation_pair_label(dataset, alert_a_id, alert_b_id, label) VALUES (?, ?, ?, ?)",
                DATASET, a, b, label);
    }
}
