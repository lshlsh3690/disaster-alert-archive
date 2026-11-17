package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.AlertFacts;
import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.PairFacts;
import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.SigunguRelation;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.LabeledPair;
import com.disaster.alert.alertapi.domain.event.evaluation.RecheckCandidateFinder.PairInput;
import com.disaster.alert.alertapi.domain.event.evaluation.RecheckCandidateFinder.Rule;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 재확인 후보 추출({@code --eval.recheck}). 기준이 바뀐 뒤 바뀐 규칙의 조건에 걸리는 라벨만 사람이 다시 보도록
 * 규칙별 후보 목록(마크다운)을 만든다. 판정은 하지 않는다(사람 몫). 규칙-조건 표는 specs/006-clustering-evaluation/plan.md 2절.
 *
 * <p>272쌍 같은 쌍 셋은 쌍 단위로, B-cubed 정답은 걸린 알림이 든 정답 사건 전체를 낸다.
 */
@Component
@Profile("eval")
@RequiredArgsConstructor
public class RecheckRunner {

    private static final DateTimeFormatter AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AssignmentReader assignmentReader;
    private final PlaceDirectoryLoader placeDirectoryLoader;

    /** "D,E,G,I" → 규칙 집합. 모르는 규칙 이름이면 실패한다. */
    public static Set<Rule> parseRules(String value) {
        Set<Rule> rules = EnumSet.noneOf(Rule.class);
        for (String token : value.split(",")) {
            String name = token.trim();
            if (!name.isEmpty()) {
                try {
                    rules.add(Rule.valueOf(name));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("--eval.recheck 에 모르는 규칙이 있습니다(D,E,G,I 중에서): " + name, e);
                }
            }
        }
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("--eval.recheck=<규칙 목록, 예: D,E,G,I> 값이 비어 있습니다");
        }
        return rules;
    }

    /** 쌍 셋과(있으면) B-cubed 정답에서 규칙별 재확인 후보를 찾아 마크다운으로 낸다. */
    public String run(String datasetName, Set<Rule> rules, List<LabeledPair> pairs, BCubedGold bcubedGold) {
        AlertPlaceFacts facts = new AlertPlaceFacts(placeDirectoryLoader.load());
        Map<Long, AlertSnapshot> snapshots = new HashMap<>();
        StringBuilder out = new StringBuilder("# 재확인 후보 — ").append(datasetName)
                .append(" (규칙 ").append(rules.stream().map(Rule::name).collect(Collectors.joining(","))).append(")\n\n")
                .append("기준: specs/001-event-clustering-pipeline/CLUSTERING_CRITERIA.md. 규칙-조건 표: specs/006-clustering-evaluation/plan.md 2절. ")
                .append("후보는 다시 볼 대상일 뿐 판정이 아니다.\n");
        out.append(pairSection(rules, pairs, facts, snapshots));
        if (bcubedGold != null) {
            out.append(bcubedSection(rules, bcubedGold, facts, snapshots));
        }
        return out.toString();
    }

    // 쌍 셋: 라벨 쌍마다 두 알림의 사실을 계산해 규칙별로 걸린 쌍과 현재 라벨을 낸다
    private String pairSection(Set<Rule> rules, List<LabeledPair> pairs, AlertPlaceFacts facts,
                               Map<Long, AlertSnapshot> snapshots) {
        Map<String, Label> labelOf = new HashMap<>();
        List<PairInput> inputs = new ArrayList<>();
        for (LabeledPair p : pairs) {
            labelOf.put(key(p.alertA(), p.alertB()), p.label());
            inputs.add(pairInput(p.alertA(), p.alertB(), facts, snapshots));
        }
        Map<Rule, List<PairInput>> found = new RecheckCandidateFinder().find(rules, inputs);

        StringBuilder out = new StringBuilder("\n## 쌍 셋 (").append(pairs.size()).append("쌍)\n\n");
        found.forEach((rule, list) -> out.append("- ").append(rule).append(": ").append(list.size()).append("쌍\n"));
        found.forEach((rule, list) -> {
            out.append("\n### ").append(rule).append(" — ").append(list.size()).append("쌍\n");
            for (PairInput p : list) {
                out.append("\n#### ").append(p.alertIdA()).append(" ↔ ").append(p.alertIdB())
                        .append(" · 현재 라벨 ").append(labelOf.get(key(p.alertIdA(), p.alertIdB()))).append('\n')
                        .append(line(snapshots.get(p.alertIdA()), p.a()))
                        .append(line(snapshots.get(p.alertIdB()), p.b()));
                if (!p.pair().sharedFacilityNames().isEmpty()) {
                    out.append("- 같은 시설 후보: ").append(String.join(", ", p.pair().sharedFacilityNames())).append('\n');
                }
            }
        });
        return out.toString();
    }

    // B-cubed: D·E·I 는 알림 하나만 보면 되므로 자기 자신과의 쌍(시설 후보 없음)으로, G 는 같은 유형 알림끼리의 쌍으로 판정한다.
    // 분류 불가(모르겠음) 알림도 판정한다 — D·I 때문에 못 나눴던 알림이 새 기준으로는 나뉠 수 있기 때문이다.
    // 걸린 알림이 든 정답 사건 전체를 다시 볼 대상으로 내고, G 는 사건을 가로지르는 짝이 중요하므로 걸린 쌍도 함께 낸다.
    private String bcubedSection(Set<Rule> rules, BCubedGold gold, AlertPlaceFacts facts,
                                 Map<Long, AlertSnapshot> snapshots) {
        List<Long> alertIds = Stream.concat(gold.clusterOf().keySet().stream(), gold.unsure().stream())
                .distinct().sorted().toList();
        Map<Rule, List<PairInput>> hits = new TreeMap<>();
        for (Rule rule : rules) {
            List<PairInput> inputs = rule == Rule.G
                    ? sameTypePairs(alertIds, facts, snapshots)
                    : alertIds.stream().map(id -> selfPair(id, facts, snapshots)).toList();
            hits.put(rule, new RecheckCandidateFinder().find(EnumSet.of(rule), inputs).get(rule));
        }

        StringBuilder out = new StringBuilder("\n## B-cubed 정답 (알림 ").append(alertIds.size())
                .append("건, 분류 불가 ").append(gold.unsure().size()).append("건 포함)\n\n");
        hits.forEach((rule, list) -> out.append("- ").append(rule).append(": 알림 ").append(alertsOf(list).size())
                .append("건, 묶음 ").append(groupsOf(alertsOf(list), gold).size()).append("개\n"));
        hits.forEach((rule, list) -> {
            out.append("\n### ").append(rule).append('\n');
            if (rule == Rule.G) {
                list.forEach(p -> out.append("- ").append(p.alertIdA()).append("(").append(groupName(p.alertIdA(), gold))
                        .append(") ↔ ").append(p.alertIdB()).append("(").append(groupName(p.alertIdB(), gold))
                        .append(") · 같은 시설 후보: ").append(String.join(", ", p.pair().sharedFacilityNames())).append('\n'));
            }
            Set<Long> hit = alertsOf(list);
            for (String group : groupsOf(hit, gold)) {
                out.append("\n#### ").append(group).append('\n');
                alertIds.stream().filter(id -> groupName(id, gold).equals(group))
                        .forEach(id -> out.append(hit.contains(id) ? "- **걸림** " : "- ")
                                .append(line(snapshots.get(id), analyze(id, facts, snapshots)).substring(2)));
            }
        });
        return out.toString();
    }

    private static Set<Long> alertsOf(List<PairInput> pairs) {
        Set<Long> ids = new LinkedHashSet<>();
        pairs.forEach(p -> ids.addAll(List.of(p.alertIdA(), p.alertIdB())));
        return ids;
    }

    // 정답 JSON 에서 알림이 속한 묶음 이름. 혼자(solo) 알림의 키는 파서가 붙인 내부 번호라 사건 번호로 보이지 않게 따로 이름 붙인다
    private static String groupName(long alertId, BCubedGold gold) {
        if (gold.unsure().contains(alertId)) {
            return "분류 불가";
        }
        Long cluster = gold.clusterOf().get(alertId);
        long size = gold.clusterOf().values().stream().filter(cluster::equals).count();
        return size == 1 ? "혼자 알림 " + alertId : "정답 사건 " + cluster;
    }

    private static Set<String> groupsOf(Set<Long> alertIds, BCubedGold gold) {
        return alertIds.stream().map(id -> groupName(id, gold)).collect(Collectors.toCollection(TreeSet::new));
    }

    private List<PairInput> sameTypePairs(List<Long> alertIds, AlertPlaceFacts facts, Map<Long, AlertSnapshot> snapshots) {
        List<PairInput> inputs = new ArrayList<>();
        for (int i = 0; i < alertIds.size(); i++) {
            for (int j = i + 1; j < alertIds.size(); j++) {
                long a = alertIds.get(i);
                long b = alertIds.get(j);
                if (snapshot(a, snapshots).type() != null && snapshot(a, snapshots).type().equals(snapshot(b, snapshots).type())) {
                    inputs.add(pairInput(a, b, facts, snapshots));
                }
            }
        }
        return inputs;
    }

    private PairInput selfPair(long id, AlertPlaceFacts facts, Map<Long, AlertSnapshot> snapshots) {
        AlertFacts f = analyze(id, facts, snapshots);
        return new PairInput(id, id, f, f, new PairFacts(SigunguRelation.SAME, Duration.ZERO, List.of()));
    }

    private PairInput pairInput(long a, long b, AlertPlaceFacts facts, Map<Long, AlertSnapshot> snapshots) {
        return new PairInput(a, b, analyze(a, facts, snapshots), analyze(b, facts, snapshots),
                facts.compare(snapshot(a, snapshots), snapshot(b, snapshots)));
    }

    private AlertFacts analyze(long id, AlertPlaceFacts facts, Map<Long, AlertSnapshot> snapshots) {
        return facts.analyze(snapshot(id, snapshots));
    }

    private AlertSnapshot snapshot(long id, Map<Long, AlertSnapshot> snapshots) {
        return snapshots.computeIfAbsent(id, assignmentReader::findAlert);
    }

    // 알림 한 줄: id · 발송 시각 · 유형 · 발신 기관(종류) · 수신 시군구 수 · 본문
    private static String line(AlertSnapshot s, AlertFacts f) {
        return "- " + s.id() + " · " + s.sentAt().format(AT) + " · " + s.type()
                + " · 발신 " + f.senderAgency().orElse("없음") + "(" + f.agencyKind() + ")"
                + " · 수신 시군구 " + f.receivedSigunguCount() + " · " + s.message().replace('\n', ' ') + "\n";
    }

    private static String key(long a, long b) {
        return Math.min(a, b) + "-" + Math.max(a, b);
    }
}
