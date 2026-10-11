package com.disaster.alert.alertapi.domain.event.evaluation;

import org.springframework.boot.ApplicationArguments;

import java.util.List;

/**
 * 평가 실행기 인자({@code --eval.*}).
 *
 * <p>dataset 만 필수다. labels 가 없으면 이미 적재된 라벨로 평가만 하고, report 가 없으면 로그로만 출력한다.
 * 같은 옵션이 여러 번 오면 첫 값을 쓴다(Spring 의 옵션 값 목록 순서 그대로).
 */
public record EvaluationRunOptions(String dataset, String labelsPath, String reportPath) {

    public static EvaluationRunOptions parse(ApplicationArguments args) {
        String dataset = first(args, "eval.dataset");
        if (dataset == null) {
            throw new IllegalArgumentException("--eval.dataset 옵션이 필요합니다");
        }
        return new EvaluationRunOptions(dataset, first(args, "eval.labels"), first(args, "eval.report"));
    }

    /** 첫 값을 trim 해서 돌려준다. 옵션이 없거나 값이 blank 면 null. */
    private static String first(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.isEmpty() || values.get(0) == null) {
            return null;
        }
        String v = values.get(0).trim();
        return v.isEmpty() ? null : v;
    }
}
