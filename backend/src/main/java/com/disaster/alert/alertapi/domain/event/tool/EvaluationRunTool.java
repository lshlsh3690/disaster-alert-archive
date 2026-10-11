package com.disaster.alert.alertapi.domain.event.tool;

import com.disaster.alert.alertapi.domain.event.evaluation.EvaluationLabelLoader;
import com.disaster.alert.alertapi.domain.event.evaluation.EvaluationReportFormatter;
import com.disaster.alert.alertapi.domain.event.evaluation.EvaluationRunOptions;
import com.disaster.alert.alertapi.domain.event.evaluation.EventEvaluationService;
import com.disaster.alert.alertapi.domain.event.evaluation.PairwiseReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 클러스터링 평가 실행기. 라벨 CSV 적재(선택) → 기존 이벤트 배정 평가 → 마크다운 리포트 출력.
 *
 * <p><b>활성 조건</b>: Spring Profile {@code eval} 활성 시에만 실행. 일반 부팅에서는 빈 등록 자체가 안 됨.
 *
 * <p><b>옵션</b>
 * <ul>
 *   <li>{@code --eval.dataset}: 필수. 라벨 묶음 이름</li>
 *   <li>{@code --eval.labels}: 선택. 라벨 CSV 경로. 있으면 해당 dataset 을 지우고 다시 적재한 뒤 평가</li>
 *   <li>{@code --eval.report}: 선택. 리포트를 저장할 경로(UTF-8, 부모 디렉터리 자동 생성). 없으면 로그로만 출력</li>
 * </ul>
 *
 * <p><b>실행 예</b>
 * <pre>
 * cd backend && set -a && source ../.env.dev && set +a
 * ./gradlew bootRun --args='--spring.profiles.active=eval --eval.dataset=cluster-eval-2026-10 --eval.labels=evaluation/datasets/cluster-eval-2026-10.csv --eval.report=evaluation/reports/before.md'
 * </pre>
 *
 * <p>파일을 읽는 일은 적재(DB 변경)보다 먼저 한다. 경로 오타로 기존 라벨만 지워지는 일이 없게 하기 위해서다.
 */
@Component
@Profile("eval")
@RequiredArgsConstructor
@Slf4j
public class EvaluationRunTool implements ApplicationRunner {

    private final EvaluationLabelLoader labelLoader;
    private final EventEvaluationService evaluationService;

    @Override
    public void run(ApplicationArguments args) {
        EvaluationRunOptions options = EvaluationRunOptions.parse(args);

        if (options.labelsPath() != null) {
            labelLoader.load(options.dataset(), readUtf8(options.labelsPath()));
        }

        PairwiseReport report = evaluationService.evaluate(options.dataset());
        String markdown = EvaluationReportFormatter.toMarkdown(options.dataset(), report);
        log.info("평가 리포트\n{}", markdown);

        if (options.reportPath() != null) {
            writeUtf8(options.reportPath(), markdown);
        }
    }

    private static String readUtf8(String path) {
        try {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("라벨 파일을 읽을 수 없습니다: " + path, e);
        }
    }

    private static void writeUtf8(String path, String text) {
        try {
            Path target = Path.of(path);
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(target, text, StandardCharsets.UTF_8);
            log.info("평가 리포트 저장: {}", target);
        } catch (IOException e) {
            throw new UncheckedIOException("리포트 파일을 쓸 수 없습니다: " + path, e);
        }
    }
}
