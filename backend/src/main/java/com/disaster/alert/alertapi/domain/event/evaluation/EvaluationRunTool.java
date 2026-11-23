package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.AssignmentReader.PairFacts;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.LabeledPair;
import com.disaster.alert.alertapi.domain.event.evaluation.PairCounter.Observation;
import com.disaster.alert.alertapi.domain.event.evaluation.RecheckCandidateFinder.Rule;
import com.disaster.alert.alertapi.domain.event.evaluation.ReportFormatter.Row;
import com.disaster.alert.alertapi.domain.event.repository.ClusteringPairLabelRepository;
import com.disaster.alert.alertapi.domain.event.repository.EvaluationLabelRepository;
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
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 클러스터링 평가 실행 진입점. 사용법은 specs/006-clustering-evaluation/quickstart.md "실행" 절.
 *
 * <p>{@code --eval.labels} 라벨 JSON 을 읽어 DB 의 이벤트 배정과 비교하고,
 * {@code --eval.dataset=<셋 이름>} 을 주면 파일 대신 관리자 화면에서 관리하는 평가 셋 라벨(evaluation_pair_label)을,
 * {@code --eval.pair-labels-as-of=<ISO 시각>} 을 주면 DB 에 저장된 불일치 쌍 사람 라벨(그 시각까지 저장·수정된 것)을 정답으로 쓴다
 * (둘 중 정확히 하나가 필수, 함께 주면 오류). {@code --eval.report}(선택) 경로에 마크다운 리포트를 쓴다. {@code --eval.bcubed}(선택)에 B-cubed 정답 JSON 을
 * 주면 알림 단위 점수 절이 리포트 맨 끝에 붙는다. {@code --eval.recheck=D,E,G,I}(선택)를 주면 채점 대신
 * 재확인 후보 목록({@link RecheckRunner})만 낸다. {@code --eval.judge-agreement}(선택, 값 없음)를 주면 채점 대신
 * 판사(LLM)가 라벨 쌍을 판정해 사람 라벨과의 일치율 리포트({@link JudgeAgreementRunner})만 낸다 - <b>유료 호출</b>이라 실행 전 승인이 필요하다.
 */
@Slf4j
@Component
@Profile("eval")
@RequiredArgsConstructor
public class EvaluationRunTool implements ApplicationRunner {

    private final AssignmentReader assignmentReader;
    private final RecheckRunner recheckRunner;
    private final JudgeAgreementRunner judgeAgreementRunner;
    private final ClusteringPairLabelRepository pairLabelRepository;
    private final EvaluationLabelRepository evaluationLabelRepository;
    private final ConfigurableApplicationContext context;

    /** 평가에 쓸 라벨 쌍과 리포트 머리에 적을 데이터셋 이름. 파일·DB 어느 출처든 이 모양으로 모은다. */
    private record LabelSource(String datasetName, List<LabeledPair> pairs) {
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        // 1) 라벨을 먼저 끝까지 읽는다 - 형식 오류면 채점(DB 배정 조회)을 시작하기 전에 실패한다. 출처는 둘 중 하나다
        //    · 파일(--eval.labels): JSON 배열 [{"q":1,"label":"SAME","a":{"id":25154,...},"b":{"id":28106,...}}, ...]
        //    · DB(--eval.dataset): 관리자 화면에서 관리하는 평가 셋 라벨(evaluation_pair_label), 셋 이름으로 고름
        //    · DB(--eval.pair-labels-as-of): 관리자가 대시보드에서 매긴 불일치 쌍 라벨(clustering_pair_label)
        //    → pairs   : [LabeledPair(25154, 28106, SAME), LabeledPair(24037, 28111, DIFF), ...]  (원문 등 사람이 보는 필드는 버림)
        //    이름과 쌍 목록만 출처에 따라 다르고, 이후 채점·리포트·재확인·판사 일치율 흐름은 둘이 똑같다
        LabelSource source = readLabelSource(args);
        String dataset = source.datasetName();
        List<LabeledPair> pairs = source.pairs();
        //    저장 경로도 여기서 확인한다 - 옵션이 잘못됐는데 채점이 다 끝난 뒤에야 실패하지 않도록
        Path reportPath = args.containsOption("eval.report")
                ? Path.of(singleValue(args, "eval.report", "--eval.report=<리포트 경로> 값이 비어 있습니다")) : null;
        //    B-cubed 정답(--eval.bcubed)도 같은 이유로 여기서 읽는다. 없으면 null(알림 단위 절을 쓰지 않음)
        BCubedGold bcubedGold = readBCubedGold(args);

        //    두 모드를 같이 주면 한쪽이 조용히 빠지므로 막는다
        if (args.containsOption("eval.recheck") && args.containsOption("eval.judge-agreement")) {
            throw new IllegalArgumentException("--eval.recheck 와 --eval.judge-agreement 는 함께 쓸 수 없습니다");
        }
        //    --eval.judge-agreement 는 값 없는 플래그다. containsOption 은 값을 보지 않아 =false 를 줘도 유료 호출이 돌므로 값이 있으면 막는다
        if (args.containsOption("eval.judge-agreement") && !args.getOptionValues("eval.judge-agreement").isEmpty()) {
            throw new IllegalArgumentException("--eval.judge-agreement 는 값 없이 씁니다(받은 값: "
                    + args.getOptionValues("eval.judge-agreement") + ")");
        }
        //    --eval.recheck 가 있으면 채점 대신 재확인 후보 목록만 내고 끝낸다(무료, 판정 없음)
        if (args.containsOption("eval.recheck")) {
            Set<Rule> rules = RecheckRunner.parseRules(
                    singleValue(args, "eval.recheck", "--eval.recheck=<규칙 목록, 예: D,E,G,I> 값이 비어 있습니다"));
            writeReport(recheckRunner.run(dataset, rules, pairs, bcubedGold), reportPath);
            System.exit(SpringApplication.exit(context, () -> 0));
            return;
        }

        //    --eval.judge-agreement 가 있으면 채점 대신 판사 일치율만 잰다(유료: 판사를 실제로 호출한다). 라벨 파일은 위에서 이미 검증됐다
        if (args.containsOption("eval.judge-agreement")) {
            writeReport(judgeAgreementRunner.run(dataset, pairs), reportPath);
            System.exit(SpringApplication.exit(context, () -> 0));
            return;
        }

        // 2) 쌍마다 두 알림의 이벤트 배정을 DB 에서 읽어 관측으로 만들고 센다
        //    pairs → 관측: [Observation(25154, 28106, SAME, assigned=true, sameEvent=false), ...]
        //    관측 → result: PairCountResult(correctlyMerged=2, missed=2, overMerged=1, excludedUnsure=0,
        //                  excludedUnassigned=0, underMergeRate=50.0, overMergeRate=33.3, wrongPairs=[(25154,28106,MISSED), ...])
        PairCountResult result = PairCounter.count(pairs.stream().map(this::observe).toList());

        // 3) 틀린 쌍(과소병합·과병합)에만 사실 4개를 붙인다
        //    WrongPair(25154, 28106, MISSED) → Row(25154, 28106, PairFacts("산불", "산불", true, 5, 0.2))
        List<Row> rows = result.wrongPairs().stream().map(w -> {
            PairFacts facts = assignmentReader.findFacts(w.alertA(), w.alertB());
            return new Row(w.alertA(), w.alertB(), w.kind(), facts);
        }).toList();

        // 4) 리포트는 항상 로그로, --eval.report 가 있으면 파일로도 쓴다
        //    마크다운(줄 단위): '# 클러스터링 평가 - cluster-eval-2026-11' → '## 요약' → '- 점수 …점' → '- 맞게 묶은 비율 …%' → '- 빠짐없이 묶은 비율 …%' → 틀린 쌍 표
        String report = ReportFormatter.format(dataset, LocalDateTime.now(), result, rows);
        //    알림 단위 점수는 쌍 리포트 맨 끝에 새 절로 붙인다 - 틀린 쌍 표의 위치가 흔들리지 않아 두 리포트를 diff 로 비교하기 쉽다
        if (bcubedGold != null) {
            report += formatBCubedSection(scoreBCubed(bcubedGold));
        }
        writeReport(report, reportPath);

        // 5) 끝나면 종료한다. System.exit 로 넘겨야 프로세스 종료 코드가 결과와 같아진다(안 넘기면 정상 실행도 1 로 끝났다)
        System.exit(SpringApplication.exit(context, () -> 0));
    }

    // 리포트는 항상 로그로, 경로가 있으면 파일로도 쓴다
    private static void writeReport(String report, Path reportPath) throws IOException {
        log.info("\n{}", report);
        if (reportPath != null) {
            Files.writeString(reportPath, report, StandardCharsets.UTF_8);
        }
    }

    // 정답에 점수 대상으로 올라간 알림(events + solo)의 이벤트 배정을 DB 에서 읽어 B-cubed 로 계산한다.
    // 분류 불가 알림은 점수에서 빠지므로 배정을 읽지 않는다(DB 조회만 늘고 결과는 같다). 미배정은 맵에 넣지 않고, 점수기가 혼자인 이벤트로 계산한다
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
                + "- 점수 대상 " + r.scored() + "건(정답 파일에 있는 알림끼리만 비교, 미배정 " + r.unassigned() + "건은 혼자인 이벤트로 포함), 제외: 분류 불가 " + r.excludedUnsure()
                + "건\n";
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

    // 라벨 출처를 정해 읽는다. 세 옵션 중 정확히 하나여야 한다 - 함께 주면 한쪽이 조용히 무시되고, 하나도 없으면 정답이 없다
    //   --eval.labels=<JSON 경로> / --eval.pair-labels-as-of=<ISO 시각>(불일치 쌍 라벨) / --eval.dataset=<셋 이름>(평가 셋 라벨, evaluation_pair_label)
    private LabelSource readLabelSource(ApplicationArguments args) throws IOException {
        List<String> given = Stream.of("eval.labels", "eval.pair-labels-as-of", "eval.dataset")
                .filter(args::containsOption).toList();
        if (given.size() > 1) {
            throw new IllegalArgumentException("라벨 출처는 하나만 줄 수 있습니다(받은 옵션: --" + String.join(", --", given) + ")");
        }
        if (given.isEmpty()) {
            throw new IllegalArgumentException("--eval.dataset=<평가 셋 이름>, --eval.labels=<라벨 JSON 경로>, "
                    + "--eval.pair-labels-as-of=<ISO 시각> 중 하나가 필요합니다");
        }
        if (args.containsOption("eval.dataset")) {
            return readDatasetFromDb(args);
        }
        if (args.containsOption("eval.pair-labels-as-of")) {
            return readLabelsFromDb(args);
        }
        Path path = Path.of(singleValue(args, "eval.labels", "--eval.labels=<라벨 JSON 경로> 값이 비어 있습니다"));
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("라벨 JSON 파일이 없습니다: " + path);
        }
        return new LabelSource(datasetName(path), LabelJson.parse(Files.readString(path, StandardCharsets.UTF_8)));
    }

    // 관리자 화면에서 관리하는 평가 셋 라벨(evaluation_pair_label)을 셋 이름으로 읽는다. 넣은 순서(id)라 리포트의 쌍 순서가 매번 같다.
    // 평가는 로컬 사본에서 돌므로, 운영에서 라벨을 고쳤다면 그 라벨을 사본에 먼저 넣어야 한다(backend/evaluation/README.md).
    private LabelSource readDatasetFromDb(ApplicationArguments args) {
        String dataset = singleValue(args, "eval.dataset", "--eval.dataset=<평가 셋 이름, 예: cluster-eval-2026-11> 값이 비어 있습니다");
        List<LabeledPair> pairs = evaluationLabelRepository.findByDatasetOrderByIdAsc(dataset).stream()
                .map(l -> new LabeledPair(l.getAlertAId(), l.getAlertBId(), l.getLabel()))
                .toList();
        if (pairs.isEmpty()) {
            throw new IllegalArgumentException("평가 셋 '" + dataset + "' 의 라벨이 이 DB 에 없습니다 — "
                    + "backend/evaluation/datasets/pair_label_data_*.sql 을 먼저 넣었는지 확인하세요");
        }
        return new LabelSource(dataset + " (DB, " + pairs.size() + "쌍)", pairs);
    }

    // DB 에 저장된 불일치 쌍 사람 라벨을 기준 시각의 묶음으로 읽는다. 0개면 채점하지 않고 멈춘다(빈 라벨로 점수를 내면 의미가 없다)
    private LabelSource readLabelsFromDb(ApplicationArguments args) {
        String raw = singleValue(args, "eval.pair-labels-as-of", "--eval.pair-labels-as-of=<ISO 시각, 예: 2026-11-25T00:00> 값이 비어 있습니다");
        LocalDateTime asOf;
        try {
            asOf = LocalDateTime.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("--eval.pair-labels-as-of 는 ISO 시각이어야 합니다(예: 2026-11-25T00:00, 받은 값: " + raw + ")", e);
        }
        //    저장소 행 → 순수 규칙이 받는 LabelRow. 기준 시각 필터·정렬은 PairLabelSnapshot 한 곳에만 둔다
        List<PairLabelSnapshot.LabelRow> rows = pairLabelRepository.findAll().stream()
                .map(l -> new PairLabelSnapshot.LabelRow(l.getAlertA(), l.getAlertB(), l.getLabel(), l.getUpdatedAt()))
                .toList();
        List<LabeledPair> pairs = PairLabelSnapshot.select(rows, asOf);
        // 모르겠음은 채점에서 빠지므로, 같은 사건·다른 사건 라벨이 하나도 없으면 채점할 쌍이 없는 것과 같다 — 0/0 점수를 내지 않고 멈춘다
        long decided = pairs.stream().filter(p -> p.label() != LabelJson.Label.UNSURE).count();
        if (decided == 0) {
            throw new IllegalArgumentException("기준 시각 " + asOf + " 까지 저장된 불일치 쌍 사람 라벨 중 같은 사건·다른 사건 라벨이 없습니다"
                    + "(기준 시각까지 " + pairs.size() + "개, 전체 " + rows.size() + "개)");
        }
        return new LabelSource("불일치 쌍 사람 라벨(DB, 기준 " + asOf + ", " + pairs.size() + "쌍)", pairs);
    }

    // 데이터셋 이름 = 파일명에서 확장자를 뗀 것
    private static String datasetName(Path labels) {
        String name = labels.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
