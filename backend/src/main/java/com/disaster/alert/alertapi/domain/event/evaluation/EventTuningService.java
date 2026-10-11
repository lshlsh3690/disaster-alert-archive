package com.disaster.alert.alertapi.domain.event.evaluation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

// Red 단계 뼈대 — 구현은 Green 에서
@Service
public class EventTuningService {

    @Transactional(readOnly = true)
    public TuningReport sweep(String dataset, List<Double> thresholds) {
        return new TuningReport(List.of(), Map.of());
    }
}
