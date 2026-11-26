package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge.Agreement;
import com.disaster.alert.alertapi.domain.event.judge.JudgeAgreementCalculator;
import com.disaster.alert.alertapi.domain.event.judge.JudgeAgreementCalculator.PairLabel;
import com.disaster.alert.alertapi.domain.event.judge.JudgeAgreementCalculator.PairVerdict;
import com.disaster.alert.alertapi.domain.event.model.JudgeAgreementRun;
import com.disaster.alert.alertapi.domain.event.repository.EvaluationLabelRepository;
import com.disaster.alert.alertapi.domain.event.repository.JudgeAgreementRunPairRepository;
import com.disaster.alert.alertapi.domain.event.repository.JudgeAgreementRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 판사 버전별 일치율을 DB 측정 기록에서 꺼낸다(specs/014). 일치율 숫자는 저장하지 않고, 그 버전의 최신 측정의 쌍별 판정과
 * 현재 사람 라벨({@code evaluation_pair_label})을 {@link JudgeAgreementCalculator} 에 넘겨 그때그때 센다. 규칙은 계산기에만 있고
 * 여기서는 읽어서 넘기기만 한다(헌법 VI).
 *
 * <p>쓰는 곳: 일일 채점(채점 순간의 값을 그날 기록에 복사), 대시보드 판사 버전 이력.
 */
@Service
@RequiredArgsConstructor
public class JudgeAgreementService {

    private final JudgeAgreementRunRepository runRepository;
    private final JudgeAgreementRunPairRepository pairRepository;
    private final EvaluationLabelRepository labelRepository;

    /**
     * 버전 하나의 현재 일치율.
     *
     * @param measuredOn      최신 측정을 마친 날
     * @param recomputable    라벨이 바뀌면 다시 계산되는 값인가(합계만 옮긴 옛 측정이면 false)
     * @param unmeasuredPairs 셋에 SAME/DIFF 라벨이 있는데 그 측정에 판정이 없는 쌍 수. 합계만 있는 측정이면 null
     */
    public record VersionAgreement(String version, String model, LocalDate measuredOn, Agreement agreement,
                                   boolean recomputable, Integer unmeasuredPairs) {
    }

    /**
     * 그 버전의 최신 측정으로 센 일치율. 측정이 없거나, 쌍별 판정이 있는데 그 셋의 라벨이 이 DB 에 하나도 없으면 비어 있다
     * (일일 채점은 이때 판사를 부르지 않고 건너뛴다 — 라벨 덤프를 넣기 전의 DB 가 그렇다).
     */
    @Transactional(readOnly = true)
    public Optional<VersionAgreement> latest(String version) {
        // 1) 최신 측정 하나 — 마친 시각이 가장 늦은 것(같으면 id 큰 것)
        Optional<JudgeAgreementRun> found = runRepository.findFirstByJudgeVersionOrderByFinishedAtDescIdDesc(version);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        JudgeAgreementRun run = found.get();
        LocalDate measuredOn = run.getFinishedAt().toLocalDate();
        // 2) 쌍별 판정이 없는 옛 측정은 저장된 합계를 그대로 쓴다
        if (!run.isHasPairVerdicts()) {
            Agreement legacy = new Agreement(run.getLegacySameNum(), run.getLegacySameDen(),
                    run.getLegacyDiffNum(), run.getLegacyDiffDen());
            return Optional.of(new VersionAgreement(version, run.getModel(), measuredOn, legacy, false, null));
        }
        // 3) 그 측정의 셋 라벨을 지금 값으로 읽는다. 하나도 없으면 셀 정답이 없다
        List<PairLabel> labels = labelRepository.findByDatasetOrderByIdAsc(run.getDataset()).stream()
                .map(l -> new PairLabel(l.getAlertAId(), l.getAlertBId(), l.getLabel()))
                .toList();
        if (labels.isEmpty()) {
            return Optional.empty();
        }
        // 4) 쌍별 판정과 맞대어 센다
        List<PairVerdict> verdicts = pairRepository.findByRunId(run.getId()).stream()
                .map(p -> new PairVerdict(p.getAlertA(), p.getAlertB(), p.getVerdict()))
                .toList();
        JudgeAgreementCalculator.Result r = JudgeAgreementCalculator.calculate(verdicts, labels);
        Agreement agreement = new Agreement(r.sameNum(), r.sameDen(), r.diffNum(), r.diffDen());
        return Optional.of(new VersionAgreement(version, run.getModel(), measuredOn, agreement, true, r.unmeasuredPairs()));
    }
}
