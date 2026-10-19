package com.disaster.alert.alertapi.domain.event.evaluation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 운영 사본 DB 의 기존 이벤트 배정(event_alert_mapping)을 평가 라벨과 비교해 쌍 단위 보고서를 만든다.
 * 평가 도구이며 운영 요청 경로에서 호출되지 않는다.
 *
 * <p>쌍마다 "두 알림이 같은 이벤트에 있는가"만 본다. 원인은 추정하지 않고 사실(유형·시군구·간격·본문 거리)만 붙인다.
 */
@Service
@RequiredArgsConstructor
public class EventEvaluationService {

    private final EvaluationLabelRepository labelRepository;
    private final EvaluationAssignmentReader reader;

    @Transactional(readOnly = true)
    public PairwiseReport evaluate(String dataset) {
        List<EvaluatedPair> pairs = new ArrayList<>();
        for (EvaluationLabel label : labelRepository.findByDatasetOrderById(dataset)) {
            pairs.add(toPair(label));
        }
        return PairwiseEvaluator.evaluate(pairs);
    }

    private EvaluatedPair toPair(EvaluationLabel label) {
        long aId = label.getAlertAId();
        long bId = label.getAlertBId();
        Optional<Long> eventA = reader.findEventId(aId);
        Optional<Long> eventB = reader.findEventId(bId);
        boolean assigned = eventA.isPresent() && eventB.isPresent();
        boolean predictedSame = assigned && eventA.equals(eventB);
        PairFacts facts = reader.findPairFacts(aId, bId).orElse(null);
        return new EvaluatedPair(aId, bId, label.getLabel(), assigned, predictedSame, facts);
    }
}
