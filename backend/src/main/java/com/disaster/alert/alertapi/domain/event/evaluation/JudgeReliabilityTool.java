package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.disasteralert.tool.AlertKindLabelFiles;
import com.disaster.alert.alertapi.domain.event.evaluation.JudgeAgreement.Item;
import com.disaster.alert.alertapi.domain.event.evaluation.JudgeAgreement.Result;
import com.disaster.alert.alertapi.global.util.ToolOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 판사({@link ClusteringJudge})가 사람 라벨과 얼마나 일치하는지 재서 로그로 남기는 측정 도구(설계안 ③ 판사 신뢰도).
 * 실제 판사를 그대로 부르고 DB 에는 아무것도 저장하지 않는다. 판사 프롬프트나 모델을 바꿀 때마다 다시 잰다.
 * 비용: 라벨된 쌍·알림 1건당 gpt-4o 호출 1회.
 *
 * <p><b>옵션</b> (pairs, kinds 중 주어진 것만 잰다)
 * <ul>
 *   <li>{@code --judge-reliability.pairs=파일}: {@code [{"q":..,"a_id":..,"b_id":..,"label":"SAME|DIFF|UNSURE"}]}</li>
 *   <li>{@code --judge-reliability.kinds=a.json,b.json}: {@code [{"id":..,"label":"사건|특보|안내|비사건|UNSURE"}]}
 *       (AlertKindEvalTool 과 같은 형식, 특보·안내는 비사건으로 합침)</li>
 *   <li>{@code --judge-reliability.kind-override=recheck.json}: 같은 형식의 재판정 라벨. kinds 에 있는 id 의 라벨만 덮어쓴다</li>
 * </ul>
 * 알림 원문은 연결된 DB(평가 사본)에서 읽는다. 끝나면 앱을 종료한다.
 * <pre>
 * ./gradlew bootRun --args='--spring.profiles.active=judge-reliability --judge-reliability.pairs=evaluation/datasets/cluster-eval-2026-10-incident.labels.json --judge-reliability.kinds=evaluation/datasets/alert-kind-test-labels.json,evaluation/datasets/alert-kind-r-labels.json --judge-reliability.kind-override=evaluation/datasets/alert-kind-recheck-labels.json'
 * </pre>
 */
@Component
@Profile("judge-reliability")
@RequiredArgsConstructor
@Slf4j
public class JudgeReliabilityTool implements ApplicationRunner {

    private static final int MESSAGE_PREVIEW = 80;

    private final DisasterAlertRepository disasterAlertRepository;
    private final ClusteringJudge judge;
    private final ConfigurableApplicationContext context;

    /** 사람 라벨이 붙은 쌍. q 는 라벨 파일의 문항 번호(보고서에서 어긋난 쌍을 가리킨다). */
    private record LabeledPair(long q, long aId, long bId, PairVerdict human) {
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> pairFiles = ToolOptions.optionList(args, "judge-reliability.pairs");
        List<String> kindFiles = ToolOptions.optionList(args, "judge-reliability.kinds");
        if (pairFiles.isEmpty() && kindFiles.isEmpty()) {
            throw new IllegalArgumentException("--judge-reliability.pairs 또는 --judge-reliability.kinds 옵션이 필요합니다");
        }
        // 1) 라벨을 읽는다 — 모르겠음은 이 단계에서 빠진다
        List<LabeledPair> pairs = new ArrayList<>();
        pairFiles.forEach(f -> readPairs(f, pairs));
        Map<Long, KindVerdict> kinds = readKinds(kindFiles, ToolOptions.optionList(args, "judge-reliability.kind-override"));

        // 2) 알림 원문을 한 번에 읽는다 — 쌍과 분류가 같은 알림을 쓸 수 있다
        Set<Long> ids = new HashSet<>(kinds.keySet());
        pairs.forEach(p -> {
            ids.add(p.aId());
            ids.add(p.bId());
        });
        Map<Long, DisasterAlert> alerts = disasterAlertRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(DisasterAlert::getId, Function.identity()));
        log.info("JudgeReliabilityTool 시작: 라벨 쌍 {}개, 라벨 알림 {}건, DB 에서 찾은 알림 {}건 (LLM 호출 최대 {}회)",
                pairs.size(), kinds.size(), alerts.size(), pairs.size() + kinds.size());

        // 3) 측정 후 로그로 보고하고 종료한다
        StringBuilder report = new StringBuilder();
        if (!pairFiles.isEmpty()) {
            measurePairs(pairs, alerts, report);
        }
        if (!kindFiles.isEmpty()) {
            measureKinds(kinds, alerts, report);
        }
        log.info("판사 신뢰도 측정 결과\n{}", report);
        System.exit(SpringApplication.exit(context, () -> 0));
    }

    private void measurePairs(List<LabeledPair> pairs, Map<Long, DisasterAlert> alerts, StringBuilder report) {
        List<Item<PairVerdict>> items = new ArrayList<>();
        Map<Long, LabeledPair> byQ = new LinkedHashMap<>();
        List<Long> notInDb = new ArrayList<>();
        for (LabeledPair p : pairs) {
            DisasterAlert a = alerts.get(p.aId());
            DisasterAlert b = alerts.get(p.bId());
            if (a == null || b == null) {
                log.warn("쌍 {} 의 알림({}, {}) 중 DB 에 없는 것이 있어 건너뜁니다", p.q(), p.aId(), p.bId());
                notInDb.add(p.q());
                continue;
            }
            // 라벨 파일의 a/b 순서는 시간순이 아니다 — 먼저 발송된 알림을 earlier 로 넘긴다
            DisasterAlert earlier = isEarlier(a, b) ? a : b;
            DisasterAlert later = earlier == a ? b : a;
            byQ.put(p.q(), p);
            items.add(new Item<>(p.q(), p.human(), judge.judgePair(earlier, later).orElse(null)));
        }
        Result r = JudgeAgreement.score(items, PairVerdict.SAME);
        report.append("[쌍] DB 에 없어 제외 ").append(notInDb.size()).append("쌍")
                .append(notInDb.isEmpty() ? "" : " " + notInDb).append('\n');
        report.append("- 사람 같음 ").append(r.positives()).append("쌍 중 판사도 같음 ")
                .append(r.positives() - r.positiveMissed().size()).append("쌍\n");
        report.append("- 사람 다름 ").append(r.negatives()).append("쌍 중 판사도 다름 ")
                .append(r.negatives() - r.negativeMissed().size()).append("쌍\n");
        report.append("- 판정 불가 ").append(r.undecided().size()).append("쌍\n");
        appendPairRows(report, "사람=같음, 판사=다름", r.positiveMissed(), byQ, alerts);
        appendPairRows(report, "사람=다름, 판사=같음", r.negativeMissed(), byQ, alerts);
        appendPairRows(report, "판정 불가", r.undecided(), byQ, alerts);
        report.append('\n');
    }

    private void measureKinds(Map<Long, KindVerdict> kinds, Map<Long, DisasterAlert> alerts, StringBuilder report) {
        List<Item<KindVerdict>> items = new ArrayList<>();
        List<Long> notInDb = new ArrayList<>();
        kinds.forEach((id, human) -> {
            DisasterAlert a = alerts.get(id);
            if (a == null) {
                log.warn("알림 {} 이 DB 에 없어 건너뜁니다", id);
                notInDb.add(id);
                return;
            }
            items.add(new Item<>(id, human, judge.judgeKind(a).orElse(null)));
        });
        Result r = JudgeAgreement.score(items, KindVerdict.INCIDENT);
        report.append("[분류] DB 에 없어 제외 ").append(notInDb.size()).append("건")
                .append(notInDb.isEmpty() ? "" : " " + notInDb).append('\n');
        report.append("- 사람 사건 ").append(r.positives()).append("건 중 판사도 사건 ")
                .append(r.positives() - r.positiveMissed().size()).append("건\n");
        report.append("- 사람 비사건 ").append(r.negatives()).append("건 중 판사도 비사건 ")
                .append(r.negatives() - r.negativeMissed().size()).append("건\n");
        report.append("- 판정 불가 ").append(r.undecided().size()).append("건\n");
        appendAlertRows(report, "사람=사건, 판사=비사건", r.positiveMissed(), alerts);
        appendAlertRows(report, "사람=비사건, 판사=사건", r.negativeMissed(), alerts);
        appendAlertRows(report, "판정 불가", r.undecided(), alerts);
    }

    /** 발송 시각이 빠른 쪽이 먼저. 같거나 시각이 없으면 id 가 작은 쪽을 먼저로 본다. */
    private static boolean isEarlier(DisasterAlert a, DisasterAlert b) {
        if (a.getCreatedAt() != null && b.getCreatedAt() != null && !a.getCreatedAt().equals(b.getCreatedAt())) {
            return a.getCreatedAt().isBefore(b.getCreatedAt());
        }
        return a.getId() < b.getId();
    }

    /** 분류 라벨 읽기 — 분류기 평가(AlertKindEvalTool)와 같은 라벨 규칙을 써야 두 측정이 같은 정답으로 잰다. */
    private static Map<Long, KindVerdict> readKinds(List<String> files, List<String> overrideFiles) {
        Map<Long, KindVerdict> human = new LinkedHashMap<>();
        AlertKindLabelFiles.read(files, overrideFiles)
                .forEach((id, kind) -> human.put(id, KindVerdict.valueOf(kind.name())));
        return human;
    }

    private static void readPairs(String path, List<LabeledPair> into) {
        int index = 0;
        for (JsonNode n : readJson(path)) {
            String where = path + " 의 " + index++ + "번째 항목";
            if (!n.hasNonNull("q") || !n.hasNonNull("a_id") || !n.hasNonNull("b_id") || !n.hasNonNull("label")) {
                throw new IllegalArgumentException(where + "에 q, a_id, b_id, label 중 없는 것이 있습니다: " + n);
            }
            String label = n.get("label").asText().trim();
            PairVerdict human = switch (label) {
                case "SAME" -> PairVerdict.SAME;
                case "DIFF" -> PairVerdict.DIFF;
                case "UNSURE" -> null;
                default -> throw new IllegalArgumentException(where + ": 알 수 없는 라벨 '" + label + "'");
            };
            // 모르겠음은 정답이 없으므로 측정에서 뺀다
            if (human != null) {
                into.add(new LabeledPair(n.get("q").asLong(), n.get("a_id").asLong(), n.get("b_id").asLong(), human));
            }
        }
    }

    private static JsonNode readJson(String path) {
        try {
            return new ObjectMapper().readTree(new File(path));
        } catch (IOException e) {
            throw new UncheckedIOException("라벨 파일을 읽을 수 없습니다: " + path, e);
        }
    }

    private static void appendPairRows(StringBuilder sb, String title, List<Long> qs, Map<Long, LabeledPair> byQ,
                                       Map<Long, DisasterAlert> alerts) {
        if (qs.isEmpty()) {
            return;
        }
        sb.append('\n').append(title).append('\n');
        for (Long q : qs) {
            LabeledPair p = byQ.get(q);
            sb.append("  q").append(q).append('\n');
            sb.append("    ").append(row(alerts.get(p.aId()))).append('\n');
            sb.append("    ").append(row(alerts.get(p.bId()))).append('\n');
        }
    }

    private static void appendAlertRows(StringBuilder sb, String title, List<Long> ids,
                                        Map<Long, DisasterAlert> alerts) {
        if (ids.isEmpty()) {
            return;
        }
        sb.append('\n').append(title).append('\n');
        ids.forEach(id -> sb.append("  ").append(row(alerts.get(id))).append('\n'));
    }

    /** id [유형] 원문 앞 80자. 본문이 없는 알림이 있어도 보고서가 깨지면 안 된다. */
    private static String row(DisasterAlert a) {
        String msg = Objects.toString(a.getMessage(), "(본문 없음)").replaceAll("\\s+", " ").trim();
        return a.getId() + " [" + a.getDisasterType() + "] " + msg.substring(0, Math.min(MESSAGE_PREVIEW, msg.length()));
    }
}
