package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.AssignmentReader.PairFacts;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.LabeledPair;
import com.disaster.alert.alertapi.domain.event.evaluation.PairCounter.Observation;
import com.disaster.alert.alertapi.domain.event.evaluation.ReportFormatter.Row;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * 클러스터링 평가 실행 진입점. 사용법은 docs/CLUSTERING_EVAL_GUIDE.md "실행" 절.
 *
 * <p>{@code --eval.labels}(필수) 라벨 JSON 을 읽어 DB 의 이벤트 배정과 비교하고,
 * {@code --eval.report}(선택) 경로에 마크다운 리포트를 쓴다. {@code --eval.bcubed}(선택)에 B-cubed 정답 JSON 을
 * 주면 알림 단위 점수 절이 리포트 맨 끝에 붙는다.
 */
@Slf4j
@Component
@Profile("eval")
@RequiredArgsConstructor
public class EvaluationRunTool implements ApplicationRunner {

    private final AssignmentReader assignmentReader;
    private final ConfigurableApplicationContext context;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        // 1) 라벨 JSON 을 먼저 끝까지 읽는다 - 형식 오류면 DB 를 건드리기 전에 실패한다
        //    JSON 배열: [{"q":1,"label":"SAME","a":{"id":25154,"message":"..."},"b":{"id":28106,"message":"..."}}, ...]
        //    → pairs   : [LabeledPair(25154, 28106, SAME), LabeledPair(24037, 28111, DIFF), ...]  (원문 등 사람이 보는 필드는 버림)
        Path labels = requiredLabelsPath(args);
        //    저장 경로도 여기서 확인한다 - 옵션이 잘못됐는데 채점이 다 끝난 뒤에야 실패하지 않도록
        Path reportPath = args.containsOption("eval.report")
                ? Path.of(singleValue(args, "eval.report", "--eval.report=<리포트 경로> 값이 비어 있습니다")) : null;
        List<LabeledPair> pairs = LabelJson.parse(Files.readString(labels, StandardCharsets.UTF_8));
        //    B-cubed 정답(--eval.bcubed)도 같은 이유로 여기서 읽는다. 없으면 null(알림 단위 절을 쓰지 않음)
        BCubedGold bcubedGold = readBCubedGold(args);

        // 2) 쌍마다 두 알림의 이벤트 배정을 DB 에서 읽어 관측으로 만들고 센다
        //    pairs → 관측: [Observation(25154, 28106, SAME, assigned=true, sameEvent=false), ...]
        //    관측 → result: PairCountResult(correctlyMerged=2, missed=2, overMerged=1, excludedUnsure=0,
        //                  excludedUnassigned=0, underMergeRate=50.0, overMergeRate=33.3, wrongPairs=[(25154,28106,MISSED), ...])
        PairCountResult result = PairCounter.count(pairs.stream().map(this::observe).toList());

        // 3) 틀린 쌍(놓침·잘못 묶음)에만 사실 4개를 붙인다
        //    WrongPair(25154, 28106, MISSED) → Row(25154, 28106, PairFacts("산불", "산불", true, 5, 0.2))
        List<Row> rows = result.wrongPairs().stream().map(w -> {
            PairFacts facts = assignmentReader.findFacts(w.alertA(), w.alertB());
            return new Row(w.alertA(), w.alertB(), w.kind(), facts);
        }).toList();

        // 4) 리포트는 항상 로그로, --eval.report 가 있으면 파일로도 쓴다
        //    마크다운(줄 단위): '# 클러스터링 평가 - cluster-eval-2026-11' → '## 요약' → '- 점수 …점' → '- 맞게 묶은 비율 …%' → '- 빠짐없이 묶은 비율 …%' → 틀린 쌍 표
        String report = ReportFormatter.format(datasetName(labels), LocalDateTime.now(), result, rows);
        //    알림 단위 점수는 쌍 리포트 맨 끝에 새 절로 붙인다 - 앞쪽 절 순서를 건드리지 않아 BaselineReportParser 가 그대로 읽는다
        if (bcubedGold != null) {
            report += formatBCubedSection(scoreBCubed(bcubedGold));
        }
        log.info("\n{}", report);
        if (reportPath != null) {
            Files.writeString(reportPath, report, StandardCharsets.UTF_8);
        }

        // 5) 끝나면 종료한다. System.exit 로 넘겨야 프로세스 종료 코드가 결과와 같아진다(안 넘기면 정상 실행도 1 로 끝났다)
        System.exit(SpringApplication.exit(context, () -> 0));
    }

    // 정답에 점수 대상으로 올라간 알림(events + solo)의 이벤트 배정을 DB 에서 읽어 B-cubed 로 계산한다.
    // 분류 불가 알림은 점수에서 빠지므로 배정을 읽지 않는다(DB 조회만 늘고 결과는 같다). 미배정은 맵에서 뺀다
    private BCubedResult scoreBCubed(BCubedGold gold) {
        Map<Long, Long> systemEventOf = new HashMap<>();
        for (Long alertId : gold.clusterOf().keySet()) {
            assignmentReader.findEventId(alertId).ifPresent(eventId -> systemEventOf.put(alertId, eventId));
        }
        return BCubedScorer.score(gold.clusterOf(), gold.unsure(), systemEventOf);
    }

    // 쉬운 말만 쓴다(영어 지표 이름 금지)
    private static String formatBCubedSection(BCubedResult r) {
        String score = r.score().isPresent() ? String.format(Locale.ROOT, "%.1f점", r.score().getAsDouble()) : "-";
        return "\n## 알림 단위 점수\n"
                + "- 점수 " + score + " — 맞게 묶은 비율과 빠짐없이 묶은 비율의 조화평균 (알림마다 구해 평균)\n"
                + "- 맞게 묶은 비율 " + percent(r.precisionRate()) + "\n"
                + "- 빠짐없이 묶은 비율 " + percent(r.completenessRate()) + "\n"
                + "- 점수 대상 " + r.scored() + "건(정답 파일에 있는 알림끼리만 비교), 제외: 분류 불가 " + r.excludedUnsure()
                + "건, 미배정 " + r.unassigned() + "건\n";
    }

    private static String percent(OptionalDouble rate) {
        return rate.isPresent() ? String.format(Locale.ROOT, "%.1f%%", rate.getAsDouble()) : "-";
    }

    // --eval.bcubed 가 없으면 null. 있으면 정답 JSON 을 끝까지 읽는다(형식 오류·검토 미완료면 여기서 실패)
    private static BCubedGold readBCubedGold(ApplicationArguments args) throws IOException {
        if (!args.containsOption("eval.bcubed")) {
            return null;
        }
        Path path = Path.of(singleValue(args, "eval.bcubed", "--eval.bcubed=<B-cubed 정답 JSON 경로> 값이 비어 있습니다"));
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("B-cubed 정답 JSON 파일이 없습니다: " + path);
        }
        return BCubedLabelJson.parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    // 두 알림 모두 이벤트에 있을 때만 assigned, 그때만 같은 이벤트 여부를 비교한다
    // 예) 25154 → 이벤트 7, 28106 → 이벤트 9 : assigned=true, sameEvent=false
    //     25154 → 이벤트 7, 28106 → 배정 없음 : assigned=false (비율에서 제외, 미배정 건수로만 센다)
    private Observation observe(LabeledPair p) {
        Optional<Long> eventA = assignmentReader.findEventId(p.alertA());
        Optional<Long> eventB = assignmentReader.findEventId(p.alertB());
        boolean assigned = eventA.isPresent() && eventB.isPresent();
        return new Observation(p.alertA(), p.alertB(), p.label(), assigned, assigned && eventA.equals(eventB));
    }

    // 같은 옵션을 두 번 주면 뒤쪽 값을 조용히 버리지 않고 실패한다 - 사용자가 믿는 입력과 실제 채점 입력이 달라지면 안 된다
    private static String singleValue(ApplicationArguments args, String name, String emptyMessage) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        if (values.size() > 1) {
            throw new IllegalArgumentException("--" + name + " 는 한 번만 줄 수 있습니다(받은 값 " + values.size() + "개: "
                    + String.join(", ", values) + ")");
        }
        return values.get(0);
    }

    private static Path requiredLabelsPath(ApplicationArguments args) {
        Path path = Path.of(singleValue(args, "eval.labels", "--eval.labels=<라벨 JSON 경로> 가 필요합니다"));
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("라벨 JSON 파일이 없습니다: " + path);
        }
        return path;
    }

    // 데이터셋 이름 = 파일명에서 확장자를 뗀 것
    private static String datasetName(Path labels) {
        String name = labels.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
