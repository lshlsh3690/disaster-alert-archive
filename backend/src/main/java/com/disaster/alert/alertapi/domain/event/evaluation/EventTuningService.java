package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.model.LabeledPair;
import com.disaster.alert.alertapi.domain.event.model.ThresholdSweep;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 라벨 쌍마다 "유효 거리"를 만들어 임계값별 병합 곡선을 낸다. 평가 도구이며 운영 요청 경로에서 호출되지 않는다.
 *
 * <p>b 시점 top-1 후보만 시뮬레이션하고(실제 클러스터링의 LLM 폴백·쿨다운은 재현하지 않는다), 이미 합쳐진 쌍은
 * 어떤 임계값에서도 합쳐진 것으로 둔다. 과거 이벤트 상태는 저장돼 있지 않아 현재 상태로 시점을 근사한다.
 */
@Service
@RequiredArgsConstructor
public class EventTuningService {

    private final EvaluationLabelRepository labelRepository;
    private final EvaluationAssignmentReader reader;

    // EventEvaluationService 와 같은 키·기본값 — 후보 윈도우가 어긋나면 곡선이 실제와 달라진다.
    @Value("${clustering.candidate-time-window-hours:168}")
    private int candidateTimeWindowHours;

    @Transactional(readOnly = true)
    public TuningReport sweep(String dataset, List<Double> thresholds) {
        List<LabeledPair> all = new ArrayList<>();
        Map<EvaluationStratum, List<LabeledPair>> byStratum = new EnumMap<>(EvaluationStratum.class);
        for (EvaluationLabel label : labelRepository.findByDatasetOrderById(dataset)) {
            if (label.getLabel() == EvaluationLabelType.UNSURE) {
                continue;
            }
            LabeledPair pair = new LabeledPair(effectiveDistance(label.getAlertAId(), label.getAlertBId()),
                    label.getLabel() == EvaluationLabelType.SAME);
            all.add(pair);
            byStratum.computeIfAbsent(label.getStratum(), k -> new ArrayList<>()).add(pair);
        }
        Map<EvaluationStratum, List<ThresholdSweep.Row>> curves = new EnumMap<>(EvaluationStratum.class);
        byStratum.forEach((stratum, pairs) -> curves.put(stratum, ThresholdSweep.sweep(pairs, thresholds)));
        return new TuningReport(ThresholdSweep.sweep(all, thresholds), curves);
    }

    /** 이미 같은 이벤트면 0.0, a 의 이벤트가 b 시점 top-1 후보면 그 거리, 그 외(계산 불가 포함)는 무한대. */
    private double effectiveDistance(long idX, long idY) {
        Optional<Long> eventX = reader.findEventId(idX);
        Optional<Long> eventY = reader.findEventId(idY);
        if (eventX.isEmpty() || eventY.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        if (eventX.equals(eventY)) {
            return 0.0;
        }
        Optional<EvaluationAssignmentReader.AlertSnapshot> x = reader.findSnapshot(idX);
        Optional<EvaluationAssignmentReader.AlertSnapshot> y = reader.findSnapshot(idY);
        if (x.isEmpty() || y.isEmpty() || !x.get().hasEmbedding() || !y.get().hasEmbedding()) {
            return Double.POSITIVE_INFINITY;
        }
        // EventEvaluationService 와 같은 규칙: 시각이 같으면 id 가 큰 쪽이 나중에 들어온 b 다.
        int cmp = x.get().createdAt().compareTo(y.get().createdAt());
        boolean xIsLater = cmp > 0 || (cmp == 0 && idX > idY);
        long bId = xIsLater ? idX : idY;
        long goldEvent = xIsLater ? eventY.get() : eventX.get();
        var bSnapshot = xIsLater ? x.get() : y.get();

        return reader.findTopCandidate(bId, bSnapshot.createdAt(), candidateTimeWindowHours)
                .filter(c -> c.eventId() == goldEvent)
                .map(EvaluationAssignmentReader.CandidateDistance::distance)
                .orElse(Double.POSITIVE_INFINITY);
    }
}
