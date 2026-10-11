package com.disaster.alert.alertapi.domain.event.evaluation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;

/**
 * 운영 사본 DB 의 기존 이벤트 배정(event_alert_mapping)을 평가 라벨과 비교해 쌍 단위 보고서를 만든다.
 */
// Red 단계 뼈대 — 구현은 Green 에서
@Service
public class EventEvaluationService {

    @Transactional(readOnly = true)
    public PairwiseReport evaluate(String dataset) {
        return new PairwiseReport(
                new Confusion(0, 0, 0, 0, 0.0, 0.0, 0.0),
                new EnumMap<>(EvaluationStratum.class),
                new ArrayList<>(),
                0);
    }
}
