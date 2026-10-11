package com.disaster.alert.alertapi.domain.event.evaluation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 라벨 CSV 를 evaluation_label 에 적재한다. 같은 dataset 을 다시 적재하면 기존 행을 모두 지우고 다시 넣는다(파일이 기준, 멱등).
 */
// Red 단계 뼈대 — 구현은 Green 에서
@Service
@RequiredArgsConstructor
public class EvaluationLabelLoader {

    private final EvaluationLabelRepository repository;

    /** @return 저장한 행 수 */
    @Transactional
    public int load(String dataset, String csvText) {
        return 0;
    }
}
