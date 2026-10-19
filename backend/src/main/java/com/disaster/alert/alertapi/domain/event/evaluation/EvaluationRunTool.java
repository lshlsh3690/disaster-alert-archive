package com.disaster.alert.alertapi.domain.event.evaluation;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 클러스터링 평가 실행 진입점. 사용법은 docs/CLUSTERING_EVAL_GUIDE.md "실행" 절.
 *
 * <p>{@code --eval.labels}(필수) CSV 를 읽어 DB 의 이벤트 배정과 비교하고,
 * {@code --eval.report}(선택) 경로에 마크다운 리포트를 쓴다.
 */
// Red 단계 뼈대 — 구현은 Green 에서
@Component
@Profile("eval")
public class EvaluationRunTool implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        // Green 에서 구현
    }
}
