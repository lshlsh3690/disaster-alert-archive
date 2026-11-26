package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.LabeledPair;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge.JudgeInput;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge.JudgeResult;
import com.disaster.alert.alertapi.domain.event.judge.JudgeVerdict;
import com.disaster.alert.alertapi.domain.event.service.JudgeAgreementService;
import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 판사 일치율 측정 도구가 결과를 DB 에 저장하는지(specs/014 US3). 판사는 가짜다 — 실제 LLM 을 부르지 않는다.
 *
 * <p>실행기는 {@code eval} 프로필 빈이라 테스트 컨텍스트에 없다. 그 프로필을 켜면 평가 도구(ApplicationRunner)가 기동 때 돌아
 * 프로세스를 끝내므로, 실제 빈(알림 조회·입력 조립·저장 서비스)에 가짜 판사를 넣어 직접 만든다.
 */
@IntegrationTest
@Transactional
class JudgeAgreementRunnerTest {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    AssignmentReader assignmentReader;
    @Autowired
    JudgeInputAssembler inputAssembler;
    @Autowired
    JudgeAgreementService agreementService;

    // 결정: 판정을 다 마친 뒤 측정 1행과 판정한 쌍마다 1행을 저장한다. 판사 호출이 실패한 쌍은 버리지 않고 CALL_FAILURE 로
    // 남기고(분모에서 빠짐), 사람이 UNSURE 로 둔 쌍은 판정하지 않으니(요금도 아낌) 저장하지 않는다.
    @Test
    void 판정을_마치면_측정과_쌍별_판정을_저장하고_호출_실패는_CALL_FAILURE_로_남긴다() {
        // 준비: 알림 6개로 쌍 3개 — ① 사람 SAME(판사 SAME) ② 사람 DIFF(판사 호출 실패) ③ 사람 UNSURE(판정 안 함)
        for (long id = 9_400_000_001L; id <= 9_400_000_006L; id++) {
            jdbc.update("INSERT INTO disaster_alert(disaster_alert_id, sn, message, created_at, disaster_type, alert_kind) "
                    + "VALUES (?, ?, ?, '2001-03-04 09:00:00', '화재', 'INCIDENT')", id, id, "알림-" + id);
        }
        ClusteringJudge judge = mock(ClusteringJudge.class);
        when(judge.judge(any())).thenAnswer(inv -> {
            JudgeInput input = inv.getArgument(0);
            if (input.first().body().contains("9400000003") || input.second().body().contains("9400000003")) {
                throw new IllegalStateException("가짜 네트워크 오류");
            }
            return new JudgeResult(JudgeVerdict.SAME, 100, 1);
        });
        JudgeAgreementRunner runner = new JudgeAgreementRunner(assignmentReader, inputAssembler, judge, agreementService);

        // 실행
        long runId = runner.run("runner-test", List.of(
                new LabeledPair(9_400_000_002L, 9_400_000_001L, Label.SAME),
                new LabeledPair(9_400_000_003L, 9_400_000_004L, Label.DIFF),
                new LabeledPair(9_400_000_005L, 9_400_000_006L, Label.UNSURE)));

        // 검증: 판사는 사람 UNSURE 를 뺀 2쌍에만 불렸다
        verify(judge, times(2)).judge(any());
        // 검증: 측정 1행 — 평가 도구가 만든 측정, 지금 판사 버전·모델, 넘긴 셋 이름, 성공한 1쌍의 토큰
        Map<String, Object> run = jdbc.queryForMap("SELECT * FROM judge_agreement_run WHERE id = ?", runId);
        assertThat(run.get("origin")).isEqualTo("CLI");
        assertThat(run.get("judge_version")).isEqualTo(ClusteringJudge.PROMPT_VERSION);
        assertThat(run.get("model")).isEqualTo(ClusteringJudge.MODEL);
        assertThat(run.get("dataset")).isEqualTo("runner-test");
        assertThat(run.get("prompt_tokens")).isEqualTo(100L);
        assertThat(run.get("has_pair_verdicts")).isEqualTo(true);
        // 검증: 쌍별 판정 2행 — ①은 (작은 id, 큰 id) 로 맞춘 SAME, ②는 CALL_FAILURE. ③은 없다
        List<Map<String, Object>> pairs = jdbc.queryForList(
                "SELECT alert_a, alert_b, verdict FROM judge_agreement_run_pair WHERE run_id = ? ORDER BY alert_a", runId);
        assertThat(pairs).hasSize(2);
        assertThat(pairs.get(0)).containsEntry("alert_a", 9_400_000_001L).containsEntry("verdict", "SAME");
        assertThat(pairs.get(1)).containsEntry("alert_a", 9_400_000_003L).containsEntry("verdict", "CALL_FAILURE");
    }
}
