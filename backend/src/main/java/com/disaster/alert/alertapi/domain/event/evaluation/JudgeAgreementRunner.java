package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.JudgeAgreementReport.CallFailure;
import com.disaster.alert.alertapi.domain.event.evaluation.JudgeAgreementCounter.PairRecord;
import com.disaster.alert.alertapi.domain.event.evaluation.JudgeInputAssembler.Session;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.LabeledPair;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge.JudgeInput;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge.JudgeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 판사 일치율 측정({@code --eval.judge-agreement}). 운영 채점과 같은 {@link ClusteringJudge} 로 라벨 쌍을 판정하고,
 * 사람 라벨과 견줘 교차표·일치율·비용을 마크다운으로 낸다. <b>판사를 실제로 호출하는 유료 모드</b>다(사람 UNSURE 를 뺀 227쌍에 약 $0.86, 2026-11-17 실측).
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

    /** 라벨 쌍(사람 UNSURE 포함)을 받아 판정·집계해 마크다운 리포트를 돌려준다. */
    public String run(String datasetName, List<LabeledPair> pairs) {
        // 1) 사람 UNSURE 쌍을 빼고 대상만 남긴다. 사실 계산기·지역 이름 표는 한 번만 만든다
        List<LabeledPair> targets = pairs.stream().filter(p -> p.label() != Label.UNSURE).toList();
        Session inputs = inputAssembler.open();
        Map<Long, AlertSnapshot> snapshots = new HashMap<>();
        log.info("판사 일치율 측정 시작: 대상 {}쌍(사람 UNSURE {}쌍 제외), 프롬프트 {}, 모델 {}",
                targets.size(), pairs.size() - targets.size(), ClusteringJudge.PROMPT_VERSION, ClusteringJudge.MODEL);

        List<PairRecord> records = new ArrayList<>();
        List<CallFailure> callFailures = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            LabeledPair p = targets.get(i);
            // 2) 알림이 DB 에 없으면(라벨 파일과 사본이 어긋남) 예외로 멈춘다 - 이건 호출 실패로 삼킬 문제가 아니다
            AlertSnapshot first = snapshots.computeIfAbsent(p.alertA(), assignmentReader::findAlert);
            AlertSnapshot second = snapshots.computeIfAbsent(p.alertB(), assignmentReader::findAlert);
            JudgeResult result = callJudge(p, first, second, inputs, callFailures);
            if (result != null) {
                records.add(new PairRecord(p.label(), result, first, second));
            }
            if ((i + 1) % PROGRESS_INTERVAL == 0) {
                log.info("판사 일치율 측정 진행: {}/{}쌍 (호출 실패 {}쌍)", i + 1, targets.size(), callFailures.size());
            }
        }
        return JudgeAgreementReport.format(datasetName, pairs.size(), pairs.size() - targets.size(),
                JudgeAgreementCounter.count(records), records, callFailures);
    }

    // 판사 호출만 예외를 쌍 단위로 잡는다: 네트워크 오류 한 건 때문에 이미 쓴 요금(앞선 판정)을 통째로 버리지 않으려는 것이다.
    // 입력 조립(사실 계산)은 외부 호출이 없는 우리 코드라 try 밖에 둔다 - 그 안의 버그가 호출 실패로 숨어 분모만 조용히 줄지 않게.
    // 실패 쌍은 형식 실패와 섞지 않고 호출 실패 목록에 id 와 사유를 남긴다(null 반환). 어디서 실패했는지는 이 warn 로그와 리포트 마지막 절에서 본다
    private JudgeResult callJudge(LabeledPair p, AlertSnapshot first, AlertSnapshot second, Session inputs,
                                  List<CallFailure> callFailures) {
        JudgeInput input = inputs.toInput(first, second);
        try {
            return judge.judge(input);
        } catch (RuntimeException e) {
            log.warn("판사 호출 실패 {} ↔ {}: {}", p.alertA(), p.alertB(), e.toString());
            callFailures.add(new CallFailure(p.alertA(), p.alertB(), e.toString()));
            return null;
        }
    }
}
