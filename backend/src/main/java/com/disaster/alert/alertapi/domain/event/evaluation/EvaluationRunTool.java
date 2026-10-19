package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.AssignmentReader.PairFacts;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelCsv.LabeledPair;
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
import java.util.List;
import java.util.Optional;

/**
 * 클러스터링 평가 실행 진입점. 사용법은 docs/CLUSTERING_EVAL_GUIDE.md "실행" 절.
 *
 * <p>{@code --eval.labels}(필수) CSV 를 읽어 DB 의 이벤트 배정과 비교하고,
 * {@code --eval.report}(선택) 경로에 마크다운 리포트를 쓴다.
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
        // 1) CSV 를 먼저 끝까지 읽는다 - 형식 오류면 DB 를 건드리기 전에 실패한다
        //    CSV 텍스트(줄 단위): 헤더 alert_a_id,alert_b_id,label,stratum,sub_type 다음 줄부터 25154,28106,SAME,GENERAL,incident_fire_same ...
        //    → pairs   : [LabeledPair(25154, 28106, SAME), LabeledPair(24037, 28111, DIFF), ...]  (stratum·sub_type 은 버림)
        Path labels = requiredLabelsPath(args);
        List<LabeledPair> pairs = LabelCsv.parse(Files.readString(labels, StandardCharsets.UTF_8));

        // 2) 쌍마다 두 알림의 이벤트 배정을 DB 에서 읽어 관측으로 만들고 센다
        //    pairs → 관측: [Observation(25154, 28106, SAME, assigned=true, sameEvent=false), ...]
        //    관측 → result: PairCountResult(correctlyMerged=2, missed=2, overMerged=1, excludedUnsure=0,
        //                  excludedUnassigned=0, underMergeRate=50.0, overMergeRate=33.3, wrongPairs=[(25154,28106,MISSED), ...])
        PairCountResult result = PairCounter.count(pairs.stream().map(this::observe).toList());

        // 3) 틀린 쌍(놓침·잘못 묶음)에만 사실 4개를 붙인다
        //    WrongPair(25154, 28106, MISSED) → Row(25154, 28106, PairFacts("산불", "산불", true, 5, 0.2))
        List<Row> rows = result.wrongPairs().stream().map(w -> {
            PairFacts facts = assignmentReader.findFacts(w.alertA(), w.alertB());
            return new Row(w.alertA(), w.alertB(), facts);
        }).toList();

        // 4) 리포트는 항상 로그로, --eval.report 가 있으면 파일로도 쓴다
        //    마크다운(줄 단위): '# 클러스터링 평가 - cluster-eval-2026-10-incident' → '## 요약' → '- 같은 사건 4쌍 중 2쌍 묶음, 2쌍 놓침 → 과소병합율 50.0%' → 틀린 쌍 표
        String report = ReportFormatter.format(datasetName(labels), LocalDateTime.now(), result, rows);
        log.info("\n{}", report);
        if (args.containsOption("eval.report")) {
            Files.writeString(Path.of(args.getOptionValues("eval.report").get(0)), report, StandardCharsets.UTF_8);
        }

        // 5) 웹 서버가 계속 떠 있지 않게 끝나면 종료한다
        SpringApplication.exit(context, () -> 0);
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

    private static Path requiredLabelsPath(ApplicationArguments args) {
        List<String> values = args.getOptionValues("eval.labels");
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("--eval.labels=<라벨 CSV 경로> 가 필요합니다");
        }
        Path path = Path.of(values.get(0));
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("라벨 CSV 파일이 없습니다: " + path);
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
