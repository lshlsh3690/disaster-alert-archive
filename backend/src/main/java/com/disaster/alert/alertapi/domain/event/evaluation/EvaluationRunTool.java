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
 * <p>라벨은 접속한 DB 에서만 읽어 이벤트 배정과 비교한다.
 * {@code --eval.dataset=<셋 이름>} 은 관리자 화면에서 관리하는 평가 셋 라벨(evaluation_pair_label)을,
 * {@code --eval.pair-labels-as-of=<ISO 시각>} 은 DB 에 저장된 불일치 쌍 사람 라벨(그 시각까지 저장·수정된 것)을 정답으로 쓴다
 * (둘 중 정확히 하나가 필수, 함께 주면 오류). {@code --eval.report}(선택) 경로에 마크다운 리포트를 쓴다. {@code --eval.bcubed}(선택)에 B-cubed 정답 JSON 을
 * 주면 알림 단위 점수 절이 리포트 맨 끝에 붙는다. {@code --eval.recheck=D,E,G,I}(선택)를 주면 채점 대신
 * 재확인 후보 목록({@link RecheckRunner})만 낸다. {@code --eval.judge-agreement}(선택, 값 없음)를 주면 채점 대신
 * 판사(LLM)가 라벨 쌍을 판정해 측정을 DB 에 저장한다({@link JudgeAgreementRunner}, --eval.dataset 만, --eval.report 불가) -
 * <b>유료 호출</b>이라 실행 전 승인이 필요하다.
 *
 * <p>{@code --eval.embedding-pairs=<별칭>}·{@code --eval.embedding-pairs-summary[=<별칭,...>]} 은 임베딩 모델 비교 1단계
 * 측정 모드다({@link EmbeddingPairRunner}, specs/012). 별칭과 그 주소·모델·차원은 {@link EmbeddingCandidates} 에 적혀 있고,
 * 요약에 값을 주지 않으면 그 목록 전체를 읽는다. --eval.dataset 과 --eval.report=<폴더> 만 받고, 다른 모드와 함께 쓸 수 없다.
 * OpenAI 후보는 {@code --eval.approve-paid-embedding}(값 없음)가 있어야 호출한다.
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

    /** 평가에 쓸 라벨 쌍과 리포트 머리에 적을 데이터셋 이름. 어느 라벨 표에서 읽든 이 모양으로 모은다. */
    private record LabelSource(String datasetName, List<LabeledPair> pairs) {
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        // 실행은 모두 backend/ 에서, eval 프로필로 평가 사본 DB 에 붙어 한다. 아래 예시의 사본은 disaster_alert_eval_v6.
        //
        // 0) 임베딩 쌍 거리 측정 모드는 채점과 입력이 전혀 달라(라벨 관계·유형을 직접 읽는다) 라벨 읽기 전에 따로 처리한다
        //    예) 저장 임베딩 거리 측정(무료):
        //    ./gradlew bootRun --args='--spring.profiles.active=eval --spring.datasource.url=jdbc:postgresql://localhost:5432/disaster_alert_eval_v6 --eval.dataset=cluster-eval-2026-11 --eval.embedding-pairs=stored --eval.report=evaluation/reports/embedding-pairs-2026-11-27'
        //    예) 두 지점 비교 리포트(값 없이 주면 EmbeddingCandidates.ALL 전체, 일부만이면 --eval.embedding-pairs-summary=stored,3small,qwen3_06b):
        //    ./gradlew bootRun --args='--spring.profiles.active=eval --spring.datasource.url=jdbc:postgresql://localhost:5432/disaster_alert_eval_v6 --eval.dataset=cluster-eval-2026-11 --eval.embedding-pairs-summary --eval.report=evaluation/reports/embedding-pairs-2026-11-27'
        //    (후보 모델 측정 예시는 runEmbeddingPairs 위 주석)
        if (args.containsOption("eval.embedding-pairs") || args.containsOption("eval.embedding-pairs-summary")) {
            runEmbeddingPairs(args);
            return;
        }
        // 1) 라벨을 먼저 끝까지 읽는다 - 라벨이 없으면 채점(DB 배정 조회)을 시작하기 전에 실패한다. 출처는 둘 중 하나다
        //    · DB(--eval.dataset): 관리자 화면에서 관리하는 평가 셋 라벨(evaluation_pair_label), 셋 이름으로 고름
        //    · DB(--eval.pair-labels-as-of): 관리자가 대시보드에서 매긴 불일치 쌍 라벨(clustering_pair_label)
        //    → pairs   : [LabeledPair(25154, 28106, SAME), LabeledPair(24037, 28111, DIFF), ...]
        //    이름과 쌍 목록만 출처에 따라 다르고, 이후 채점·리포트·재확인·판사 일치율 흐름은 둘이 똑같다
        //    예) 평가 셋 라벨로 채점:
        //    ./gradlew bootRun --args='--spring.profiles.active=eval --spring.datasource.url=jdbc:postgresql://localhost:5432/disaster_alert_eval_v6 --eval.dataset=cluster-eval-2026-11 --eval.report=evaluation/reports/<새 리포트 이름>.md'
        //    예) 불일치 쌍 사람 라벨로 채점:
        //    ./gradlew bootRun --args='--spring.profiles.active=eval --spring.datasource.url=jdbc:postgresql://localhost:5432/disaster_alert_eval_v6 --eval.pair-labels-as-of=2026-11-25T00:00 --eval.report=evaluation/reports/<새 리포트 이름>.md'
        LabelSource source = readLabelSource(args);
        String dataset = source.datasetName();
        List<LabeledPair> pairs = source.pairs();
        //    저장 경로도 여기서 확인한다 - 옵션이 잘못됐는데 채점이 다 끝난 뒤에야 실패하지 않도록
        Path reportPath = args.containsOption("eval.report")
                ? Path.of(singleValue(args, "eval.report", "--eval.report=<리포트 경로> 값이 비어 있습니다")) : null;
        //    B-cubed 정답(--eval.bcubed)도 같은 이유로 여기서 읽는다. 없으면 null(알림 단위 절을 쓰지 않음)
        //    예) 쌍 채점 + 알림 단위 점수:
        //    ./gradlew bootRun --args='--spring.profiles.active=eval --spring.datasource.url=jdbc:postgresql://localhost:5432/disaster_alert_eval_v6 --eval.dataset=cluster-eval-2026-11 --eval.bcubed=evaluation/datasets/bcubed-2026-05.labels.json --eval.report=evaluation/reports/<새 리포트 이름>.md'
        BCubedGold bcubedGold = readBCubedGold(args);

        //    두 모드를 같이 주면 한쪽이 조용히 빠지므로 막는다
        if (args.containsOption("eval.recheck") && args.containsOption("eval.judge-agreement")) {
            throw new IllegalArgumentException("--eval.recheck 와 --eval.judge-agreement 는 함께 쓸 수 없습니다");
        }
        //    판사 일치율 측정의 옵션 조합은 판사를 부르기 전에 확인한다(돈이 나간 뒤 실패하지 않게)
        if (args.containsOption("eval.judge-agreement")) {
            judgeAgreementDataset(args);
        }
        //    --eval.judge-agreement 는 값 없는 플래그다. containsOption 은 값을 보지 않아 =false 를 줘도 유료 호출이 돌므로 값이 있으면 막는다
        if (args.containsOption("eval.judge-agreement") && !args.getOptionValues("eval.judge-agreement").isEmpty()) {
            throw new IllegalArgumentException("--eval.judge-agreement 는 값 없이 씁니다(받은 값: "
                    + args.getOptionValues("eval.judge-agreement") + ")");
        }
        //    --eval.recheck 가 있으면 채점 대신 재확인 후보 목록만 내고 끝낸다(무료, 판정 없음)
        //    예) ./gradlew bootRun --args='--spring.profiles.active=eval --spring.datasource.url=jdbc:postgresql://localhost:5432/disaster_alert_eval_v6 --eval.dataset=cluster-eval-2026-11 --eval.bcubed=evaluation/datasets/bcubed-2026-05.labels.json --eval.recheck=D,E,G,I --eval.report=evaluation/reports/<후보 목록 이름>.md'
        if (args.containsOption("eval.recheck")) {
            Set<Rule> rules = RecheckRunner.parseRules(
                    singleValue(args, "eval.recheck", "--eval.recheck=<규칙 목록, 예: D,E,G,I> 값이 비어 있습니다"));
            writeReport(recheckRunner.run(dataset, rules, pairs, bcubedGold), reportPath);
            System.exit(SpringApplication.exit(context, () -> 0));
            return;
        }

        //    --eval.judge-agreement 가 있으면 채점 대신 판사 일치율만 재서 측정을 DB 에 저장한다(유료: 판사를 실제로 호출한다).
        //    라벨은 위에서 이미 읽었다. 결과는 파일이 아니라 DB(judge_agreement_run)에 남는다(specs/014)
        //    예) (유료, 실행 전 승인) --eval.report 는 주지 않는다:
        //    ./gradlew bootRun --args='--spring.profiles.active=eval --spring.datasource.url=jdbc:postgresql://localhost:5432/disaster_alert_eval_v6 --eval.dataset=cluster-eval-2026-11 --eval.judge-agreement'
        if (args.containsOption("eval.judge-agreement")) {
            judgeAgreementRunner.run(judgeAgreementDataset(args), pairs);
            System.exit(SpringApplication.exit(context, () -> 0));
            return;
        }

        // 2) 쌍마다 두 알림의 이벤트 배정을 DB 에서 읽어 관측으로 만들고 센다
        //    pairs → 관측: [Observation(25154, 28106, SAME, assigned=true, sameEvent=false), ...]
        //    관측 → result: PairCountResult(correctlyMerged=2, missed=2, overMerged=1, correctlySeparated=1, excludedUnsure=0,
        //                  excludedUnassigned=0, underMergeRate=50.0, overMergeRate=33.3, wrongPairs=[(25154,28106,MISSED), ...])
        PairCountResult result = PairCounter.count(pairs.stream().map(this::observe).toList());

        // 3) 틀린 쌍(과소병합·과병합)에만 사실 4개를 붙인다
        //    WrongPair(25154, 28106, MISSED) → Row(25154, 28106, PairFacts("산불", "산불", true, 5, 0.2))
        List<Row> rows = result.wrongPairs().stream().map(w -> {
            PairFacts facts = assignmentReader.findFacts(w.alertA(), w.alertB());
            return new Row(w.alertA(), w.alertB(), w.kind(), facts);
        }).toList();

        // 4) 리포트는 균형 정확도·두 비율·보조 F1·판단 없는 기준선 순이며(기준선은 포매터가 개수에서 구한다),
        //    항상 로그로 쓰고 경로가 있으면 파일로도 쓴다.
        String report = ReportFormatter.format(dataset, LocalDateTime.now(), result, rows);
        //    알림 단위 점수는 쌍 리포트 맨 끝에 새 절로 붙인다 - 틀린 쌍 표의 위치가 흔들리지 않아 두 리포트를 diff 로 비교하기 쉽다
        if (bcubedGold != null) {
            report += formatBCubedSection(scoreBCubed(bcubedGold));
        }
        writeReport(report, reportPath);

        // 5) 끝나면 종료한다. System.exit 로 넘겨야 프로세스 종료 코드가 결과와 같아진다(안 넘기면 정상 실행도 1 로 끝났다)
        System.exit(SpringApplication.exit(context, () -> 0));
    }

    // 임베딩 쌍 거리 측정(--eval.embedding-pairs) 또는 두 지점 비교 리포트(--eval.embedding-pairs-summary).
    // 옵션 조합은 임베딩을 부르기 전에 모두 확인한다(돈이 나간 뒤 실패하지 않게). 요약에서 기준선 재현이 실패하면 종료 코드 1
    // 후보의 주소·모델·차원은 EmbeddingCandidates 에 적혀 있어 명령에는 별칭만 준다. TEI 후보는 그 포트에 컨테이너가 떠 있어야 한다(contracts 1절).
    // 예) 공개 모델(TEI, 무료):
    //   ./gradlew bootRun --args='--spring.profiles.active=eval --spring.datasource.url=jdbc:postgresql://localhost:5432/disaster_alert_eval_v6 --eval.dataset=cluster-eval-2026-11 --eval.embedding-pairs=qwen3_06b --eval.report=evaluation/reports/embedding-pairs-2026-11-27'
    // 예) OpenAI 후보(유료) — --eval.approve-paid-embedding 없이 먼저 돌려 예상 비용만 보고, 승인 뒤 --eval.approve-paid-embedding 을 붙여 다시 돌린다:
    //   ./gradlew bootRun --args='--spring.profiles.active=eval --spring.datasource.url=jdbc:postgresql://localhost:5432/disaster_alert_eval_v6 --eval.dataset=cluster-eval-2026-11 --eval.embedding-pairs=3large --eval.report=evaluation/reports/embedding-pairs-2026-11-27'
    //   ./gradlew bootRun --args='--spring.profiles.active=eval --spring.datasource.url=jdbc:postgresql://localhost:5432/disaster_alert_eval_v6 --eval.dataset=cluster-eval-2026-11 --eval.embedding-pairs=3large --eval.report=evaluation/reports/embedding-pairs-2026-11-27 --eval.approve-paid-embedding'
    private void runEmbeddingPairs(ApplicationArguments args) throws IOException {
        boolean measure = args.containsOption("eval.embedding-pairs");
        boolean summary = args.containsOption("eval.embedding-pairs-summary");
        //    한 번에 한 모드만 - 둘을 같이 주면 한쪽이 조용히 빠진다. 기존 두 모드와도 입력이 달라 함께 쓸 수 없다
        if (measure && summary) {
            throw new IllegalArgumentException("--eval.embedding-pairs 와 --eval.embedding-pairs-summary 는 함께 쓸 수 없습니다");
        }
        if (args.containsOption("eval.recheck") || args.containsOption("eval.judge-agreement")) {
            throw new IllegalArgumentException("--eval.embedding-pairs(-summary) 는 --eval.recheck·--eval.judge-agreement 와 함께 쓸 수 없습니다");
        }
        //    라벨은 평가 셋(관계 포함)에서만 읽는다. 결과는 폴더에 파일로 쓰므로 --eval.report 는 폴더 경로다
        if (!args.containsOption("eval.dataset")) {
            throw new IllegalArgumentException("--eval.embedding-pairs(-summary) 는 --eval.dataset=<평가 셋 이름> 이 필요합니다");
        }
        String dataset = singleValue(args, "eval.dataset", "--eval.dataset=<평가 셋 이름> 값이 비어 있습니다");
        Path folder = Path.of(singleValue(args, "eval.report", "--eval.report=<결과 폴더> 가 필요합니다"));
        //    --eval.approve-paid-embedding(OpenAI 유료 임베딩 호출 승인)은 값 없는 플래그다. =false 를 줘도 containsOption 은 true 라 유료 호출이 나가므로 값이 있으면 막는다
        if (args.containsOption("eval.approve-paid-embedding") && !args.getOptionValues("eval.approve-paid-embedding").isEmpty()) {
            throw new IllegalArgumentException("--eval.approve-paid-embedding 은 값 없이 씁니다(받은 값: " + args.getOptionValues("eval.approve-paid-embedding") + ")");
        }
        EmbeddingPairRunner runner = context.getBean(EmbeddingPairRunner.class);
        boolean ok = true;
        if (measure) {
            EmbeddingCandidates.EmbeddingModelCandidate candidate = EmbeddingCandidates.find(
                    singleValue(args, "eval.embedding-pairs", "--eval.embedding-pairs=<별칭> 값이 비어 있습니다"));
            runner.measure(dataset, candidate, folder, args.containsOption("eval.approve-paid-embedding"));
        } else {
            //    값 없이 주면 전체 후보, 값이 있으면 그 별칭들만(두 번 주면 singleValue 가 막는다)
            List<String> values = args.getOptionValues("eval.embedding-pairs-summary");
            String csv = values.isEmpty() ? null : singleValue(args, "eval.embedding-pairs-summary", "");
            ok = runner.summarize(dataset, EmbeddingCandidates.parseList(csv), folder);
        }
        int exitCode = ok ? 0 : 1;
        System.exit(SpringApplication.exit(context, () -> exitCode));
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

    // 판사 일치율 측정이 쓸 셋 이름. 측정 기록에 남아 일치율을 셀 때 그 셋의 현재 라벨을 쓴다(specs/014 계약 3절).
    //   --eval.report: 리포트 파일은 없앴다 — 조용히 무시하면 파일이 생긴 줄 알게 되므로 막는다
    //   --eval.pair-labels-as-of: 불일치 쌍 라벨은 판사·시스템이 갈린 어려운 쌍만 모은 것이라 버전 일치율과 뜻이 달라 막는다
    private static String judgeAgreementDataset(ApplicationArguments args) {
        if (args.containsOption("eval.report")) {
            throw new IllegalArgumentException("--eval.judge-agreement 는 리포트 파일을 쓰지 않습니다(결과는 DB 에 저장) — --eval.report 를 빼세요");
        }
        if (!args.containsOption("eval.dataset")) {
            throw new IllegalArgumentException("--eval.judge-agreement 는 --eval.dataset=<평가 셋 이름> 으로만 잽니다"
                    + "(--eval.pair-labels-as-of 의 불일치 쌍 라벨은 쓰지 않습니다)");
        }
        return singleValue(args, "eval.dataset", "--eval.dataset=<평가 셋 이름> 값이 비어 있습니다");
    }

    // 라벨 출처를 정해 읽는다. 두 옵션 중 정확히 하나여야 한다 - 함께 주면 한쪽이 조용히 무시되고, 하나도 없으면 정답이 없다
    //   --eval.dataset=<셋 이름>(평가 셋 라벨, evaluation_pair_label) / --eval.pair-labels-as-of=<ISO 시각>(불일치 쌍 라벨)
    private LabelSource readLabelSource(ApplicationArguments args) {
        List<String> given = Stream.of("eval.pair-labels-as-of", "eval.dataset")
                .filter(args::containsOption).toList();
        if (given.size() > 1) {
            throw new IllegalArgumentException("라벨 출처는 하나만 줄 수 있습니다(받은 옵션: --" + String.join(", --", given) + ")");
        }
        if (given.isEmpty()) {
            throw new IllegalArgumentException("--eval.dataset=<평가 셋 이름>, --eval.pair-labels-as-of=<ISO 시각> 중 하나가 필요합니다");
        }
        return args.containsOption("eval.dataset") ? readDatasetFromDb(args) : readLabelsFromDb(args);
    }

    // 관리자 화면에서 관리하는 평가 셋 라벨(evaluation_pair_label)을 셋 이름으로 읽는다. 넣은 순서(id)라 리포트의 쌍 순서가 매번 같다.
    // 평가는 로컬 사본에서 돌므로, 운영에서 라벨을 고쳤다면 그 라벨을 사본에 먼저 넣어야 한다(backend/evaluation/README.md).
    private LabelSource readDatasetFromDb(ApplicationArguments args) {
        String dataset = singleValue(args, "eval.dataset", "--eval.dataset=<평가 셋 이름, 예: cluster-eval-2026-11> 값이 비어 있습니다");
        List<LabeledPair> pairs = evaluationLabelRepository.findByDatasetOrderByIdAsc(dataset).stream()
                .map(l -> new LabeledPair(l.getAlertAId(), l.getAlertBId(), l.getLabel()))
                .toList();
        if (pairs.isEmpty()) {
            throw new IllegalArgumentException("평가 셋 '" + dataset + "' 의 라벨이 이 DB 의 evaluation_pair_label 테이블에 없습니다 — "
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
            throw new IllegalArgumentException("기준 시각 " + asOf + " 까지 이 DB 의 clustering_pair_label 테이블에 저장된 불일치 쌍 사람 라벨 중 같은 사건·다른 사건 라벨이 없습니다"
                    + "(기준 시각까지 " + pairs.size() + "개, 전체 " + rows.size() + "개)");
        }
        return new LabelSource("불일치 쌍 사람 라벨(DB, 기준 " + asOf + ", " + pairs.size() + "쌍)", pairs);
    }
}
