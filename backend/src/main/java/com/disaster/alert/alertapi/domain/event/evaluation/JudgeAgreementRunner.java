package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.JudgeAgreementCounter.PairRecord;
import com.disaster.alert.alertapi.domain.event.evaluation.JudgeInputAssembler.Session;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.LabeledPair;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge.JudgeInput;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge.JudgeResult;
import com.disaster.alert.alertapi.domain.event.judge.JudgeAgreementCalculator.PairVerdict;
import com.disaster.alert.alertapi.domain.event.judge.JudgeVerdict;
import com.disaster.alert.alertapi.domain.event.judge.JudgeVersionCatalog;
import com.disaster.alert.alertapi.domain.event.judge.RecordedVerdict;
import com.disaster.alert.alertapi.domain.event.service.JudgeAgreementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 판사 일치율 측정({@code --eval.judge-agreement}). 운영 채점과 같은 {@link ClusteringJudge} 로 라벨 쌍을 판정하고,
 * 측정 1건과 쌍별 판정을 접속한 DB 에 저장한다(specs/014). <b>판사를 실제로 호출하는 유료 모드</b>다(v2 225쌍 약 $0.05,
 * v1 227쌍 약 $0.86 실측). 마크다운 리포트는 2026-11-26 에 없앴고, 콘솔에 요약만 남긴다 — 일치율은 저장한 판정과 현재 라벨로
 * 관리자 화면에서 본다.
 *
 * <p>사람 UNSURE 쌍은 정답이 없어 판정하지 않는다(요금도 아낀다). 쌍은 순차로 판정한다 - 수백 쌍이라 동시 호출로 얻는 이득이
 * 작고, 요금 한도·재시도 문제를 만들지 않으려는 선택이다.
 */
@Slf4j
@Component
@Profile("eval")
@RequiredArgsConstructor
public class JudgeAgreementRunner {

    // 진행 로그 간격. 272쌍을 50쌍마다 한 줄이면 5~6줄이라 유료 실행이 멈췄는지 아닌지 알 만큼만 남긴다
    private static final int PROGRESS_INTERVAL = 50;

    private final AssignmentReader assignmentReader;
    private final JudgeInputAssembler inputAssembler;
    private final ClusteringJudge judge;
    private final JudgeAgreementService agreementService;

    /**
     * 라벨 쌍(사람 UNSURE 포함)을 판정해 측정으로 저장하고, 저장한 측정 id 를 돌려준다.
     *
     * @param dataset 평가 셋 이름(--eval.dataset 값). 측정 기록에 그대로 남아 일치율을 셀 때 이 셋의 현재 라벨을 쓴다
     */
    public long run(String dataset, List<LabeledPair> pairs) {
        // 1) 판사 버전 목록에 단가가 없으면 비용을 적을 수 없다 — 돈이 나가기 전에 멈춘다
        JudgeVersionCatalog.Entry entry = JudgeVersionCatalog.find(ClusteringJudge.PROMPT_VERSION)
                .orElseThrow(() -> new IllegalStateException("판사 버전 " + ClusteringJudge.PROMPT_VERSION
                        + " 이 JudgeVersionCatalog 에 없습니다 — 모델·단가를 먼저 추가하세요"));
        // 2) 사람 UNSURE 쌍을 빼고 대상만 남긴다. 사실 계산기·지역 이름 표는 한 번만 만든다
        List<LabeledPair> targets = pairs.stream().filter(p -> p.label() != Label.UNSURE).toList();
        Session inputs = inputAssembler.open();
        Map<Long, AlertSnapshot> snapshots = new HashMap<>();
        log.info("판사 일치율 측정 시작: 셋 {}, 대상 {}쌍(사람 UNSURE {}쌍 제외), 프롬프트 {}, 모델 {}",
                dataset, targets.size(), pairs.size() - targets.size(), ClusteringJudge.PROMPT_VERSION, ClusteringJudge.MODEL);

        // 3) 쌍마다 판정한다. 저장할 판정(호출 실패 포함)과 콘솔 요약용 집계 재료를 함께 모은다
        List<PairVerdict> verdicts = new ArrayList<>();
        List<PairRecord> records = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            LabeledPair p = targets.get(i);
            // 알림이 DB 에 없으면(라벨과 사본이 어긋남) 예외로 멈춘다 - 이건 호출 실패로 삼킬 문제가 아니다
            AlertSnapshot first = snapshots.computeIfAbsent(p.alertA(), assignmentReader::findAlert);
            AlertSnapshot second = snapshots.computeIfAbsent(p.alertB(), assignmentReader::findAlert);
            JudgeResult result = callJudge(p, inputs.toInput(first, second));
            if (result == null) {
                verdicts.add(new PairVerdict(p.alertA(), p.alertB(), RecordedVerdict.CALL_FAILURE));
            } else {
                verdicts.add(new PairVerdict(p.alertA(), p.alertB(), RecordedVerdict.of(result.verdict())));
                records.add(new PairRecord(p.label(), result, first, second));
            }
            if ((i + 1) % PROGRESS_INTERVAL == 0) {
                log.info("판사 일치율 측정 진행: {}/{}쌍", i + 1, targets.size());
            }
        }

        // 4) 판정을 다 마친 뒤에만 한 번에 저장한다 — 도중에 멈추면 기록이 남지 않아 다시 잰다
        JudgeAgreementResult summary = JudgeAgreementCounter.count(records);
        double usd = entry.estimateUsd(summary.promptTokens(), summary.completionTokens());
        long runId = agreementService.saveRun(ClusteringJudge.PROMPT_VERSION, ClusteringJudge.MODEL, dataset, verdicts,
                summary.promptTokens(), summary.completionTokens(), usd);
        logSummary(runId, summary, verdicts.size() - records.size(), usd);
        return runId;
    }

    // 판사 호출만 예외를 쌍 단위로 잡는다: 네트워크 오류 한 건 때문에 이미 쓴 요금(앞선 판정)을 통째로 버리지 않으려는 것이다.
    // 입력 조립(사실 계산)은 외부 호출이 없는 우리 코드라 호출 전에 끝낸다 - 그 안의 버그가 호출 실패로 숨어 분모만 조용히 줄지 않게.
    // 실패 쌍은 형식 실패와 섞지 않고 호출 실패(CALL_FAILURE)로 저장한다(null 반환). 어디서 실패했는지는 이 warn 로그로 본다
    private JudgeResult callJudge(LabeledPair p, JudgeInput input) {
        try {
            return judge.judge(input);
        } catch (RuntimeException e) {
            log.warn("판사 호출 실패 {} ↔ {}: {}", p.alertA(), p.alertB(), e.toString());
            return null;
        }
    }

    // 콘솔 요약: 측정 순간의 라벨로 센 값이다. 관리자 화면은 저장한 판정을 그때의 현재 라벨로 다시 센다
    private static void logSummary(long runId, JudgeAgreementResult r, int callFailures, double usd) {
        log.info("판사 일치율 측정 저장: 측정 id {}", runId);
        log.info("  같은 사건 {}/{}, 다른 사건 {}/{} (판사 UNSURE {} · 형식 실패 {} · 호출 실패 {})",
                r.agreement().sameNum(), r.agreement().sameDen(), r.agreement().diffNum(), r.agreement().diffDen(),
                r.judgeUnsure(), r.formatFailures(), callFailures);
        log.info("  교차표(사람→판사): SAME→SAME {} · SAME→DIFF {} · SAME→UNSURE {} · DIFF→DIFF {} · DIFF→SAME {} · DIFF→UNSURE {}",
                r.cell(Label.SAME, JudgeVerdict.SAME), r.cell(Label.SAME, JudgeVerdict.DIFF), r.cell(Label.SAME, JudgeVerdict.UNSURE),
                r.cell(Label.DIFF, JudgeVerdict.DIFF), r.cell(Label.DIFF, JudgeVerdict.SAME), r.cell(Label.DIFF, JudgeVerdict.UNSURE));
        log.info("  토큰 입력 {} · 출력 {}, 추정 비용(상한) ${}", r.promptTokens(), r.completionTokens(),
                String.format(Locale.ROOT, "%.4f", usd));
    }
}
