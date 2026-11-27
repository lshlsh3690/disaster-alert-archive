package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.EmbeddingPairComparison.PairKey;
import com.disaster.alert.alertapi.domain.event.evaluation.EmbeddingPairComparison.Result;
import com.disaster.alert.alertapi.domain.event.evaluation.EmbeddingPairCsv.Content;
import com.disaster.alert.alertapi.domain.event.evaluation.EmbeddingPairCsv.Row;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 모델별 거리 파일들로 두 지점 비교 리포트(embedding-pairs-summary.md)를 만드는 순수 포매터(specs/012 data-model 2절,
 * JPA·Spring import 없음). 모든 지표는 "N쌍 중 M쌍"으로 쓴다. AUC·임계값 훑기 표는 만들지 않는다(spec FR-007).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EmbeddingPairSummaryFormatter {

    /** 기준선(3-small)이 다른 사건을 붙이는 두 지점: 바로 병합 거리, LLM 후보 거리. */
    public static final double IMMEDIATE_POINT = 0.15;
    public static final double LLM_CANDIDATE_POINT = 0.40;
    /** 3small 다시 임베딩 결과와 저장된 임베딩 거리의 쌍별 차이 허용치(SC-002). */
    public static final double REPRODUCTION_TOLERANCE = 0.01;

    private static final String[] POINT_NAMES = {"바로 병합 지점", "LLM 후보 지점"};
    private static final double[] POINTS = {IMMEDIATE_POINT, LLM_CANDIDATE_POINT};

    /** 기준선 재현 확인 결과. checked=false 면 3small 파일이 없어 확인하지 못한 것이다. */
    public record Reproduction(boolean checked, double maxGap) {
        public boolean failed() {
            return checked && maxGap > REPRODUCTION_TOLERANCE;
        }
    }

    /** 모델 하나의 거리 파일. */
    public record Model(String alias, Content content) {
    }

    /**
     * @param excluded  대상에서 빠진 쌍 수와 이유(데이터셋 라벨에서 센 값)
     * @param baseline  stored 거리 파일 — 모든 비교의 기준
     * @param models    stored 를 뺀 모델들(요청한 순서)
     * @param messages  알림 id → 원문(바뀐 쌍·기준을 정한 쌍을 보이는 데 쓴다)
     */
    public record Input(String dataset, EmbeddingPairSubset.Result excluded, Content baseline, List<Model> models,
                        Map<Long, String> messages, Reproduction reproduction) {
    }

    /** stored 와 3small 의 쌍별 거리 차이 최댓값. 3small 이 없으면 확인 못 한 것(checked=false). 두 파일의 쌍이 다르면 예외. */
    public static Reproduction reproduction(Content stored, Content threeSmall) {
        if (threeSmall == null) {
            return new Reproduction(false, 0.0);
        }
        Map<PairKey, Double> small = new HashMap<>();
        threeSmall.rows().forEach(r -> small.put(new PairKey(r.alertA(), r.alertB()), r.distance()));
        double max = 0.0;
        for (Row r : stored.rows()) {
            Double other = small.get(new PairKey(r.alertA(), r.alertB()));
            if (other == null) {
                throw new IllegalArgumentException("3small 거리 파일에 stored 의 쌍이 없습니다: " + r.alertA() + ", " + r.alertB());
            }
            max = Math.max(max, Math.abs(r.distance() - other));
        }
        return new Reproduction(true, max);
    }

    public static String format(Input in) {
        StringBuilder sb = new StringBuilder("# 임베딩 쌍 거리 비교 (1단계)\n\n");
        if (in.reproduction().failed()) {
            sb.append("> **기준선 재현 실패 — 후보 비교 무효** (3small 과 stored 의 쌍별 거리 차이 최댓값 ")
                    .append(fmt(in.reproduction().maxGap())).append(" > ").append(REPRODUCTION_TOLERANCE).append(")\n\n");
        }
        appendHeader(sb, in);
        for (int i = 0; i < POINTS.length; i++) {
            appendTable(sb, in, POINT_NAMES[i], POINTS[i]);
        }
        for (Model m : in.models()) {
            appendDetail(sb, in, m);
        }
        return sb.toString();
    }

    // 머리: 대상 쌍·제외 쌍·기준선 재현·모델별 실제 토큰과 비용
    private static void appendHeader(StringBuilder sb, Input in) {
        List<Row> rows = in.baseline().rows();
        sb.append("- 평가 셋 ").append(in.dataset()).append("\n");
        sb.append("- 대상 쌍: 같은 사건 ").append(count(rows, labelIs(Label.SAME))).append("쌍, 다른 사건 ")
                .append(count(rows, labelIs(Label.DIFF))).append("쌍 (화재만: 같은 사건 ")
                .append(count(rows, labelIs(Label.SAME).and(Row::isFire))).append("쌍, 다른 사건 ")
                .append(count(rows, labelIs(Label.DIFF).and(Row::isFire))).append("쌍)\n");
        sb.append("- 제외 쌍: 키 경로 유형 ").append(in.excluded().excludedKeyPathType()).append("쌍, 먼 시군구 ")
                .append(in.excluded().excludedNotNear()).append("쌍, 사람 모르겠음 ")
                .append(in.excluded().excludedUnsure()).append("쌍\n");
        Reproduction r = in.reproduction();
        sb.append("- 기준선 재현(3small 과 stored 의 쌍별 거리 차이 최댓값): ")
                .append(r.checked() ? fmt(r.maxGap()) + " (허용 " + REPRODUCTION_TOLERANCE + " 이하, "
                        + (r.failed() ? "실패" : "통과") + ")" : "3small 거리 파일이 없어 확인하지 못함 — 비교는 잠정").append("\n");
        for (Model m : in.models()) {
            Map<String, String> h = m.content().header();
            sb.append("- ").append(m.alias()).append(": 모델 ").append(h.getOrDefault("model", "-"))
                    .append(", 실제 토큰 ").append(h.getOrDefault("tokens", "-"))
                    .append(", 비용 $").append(h.getOrDefault("cost_usd", "-")).append("\n");
        }
        sb.append("\n");
    }

    // 지점 하나의 표: 기준선 행(그 지점 거리 그대로)과 모델마다 행(다른 사건 K쌍만 붙이는 거리)
    private static void appendTable(StringBuilder sb, Input in, String name, double point) {
        List<Row> base = in.baseline().rows();
        sb.append("## ").append(name).append(" (기준선 ").append(point).append(" 이하)\n\n");
        sb.append("| 모델 | 기준 거리 | 다른 사건 | 같은 사건 | 화재 같은 사건 | 화재 다른 사건 |\n|---|---|---|---|---|---|\n");
        sb.append(tableRow("stored(기준선)", point, base, point)).append("\n");
        for (Model m : in.models()) {
            Result r = compare(in, m, point);
            sb.append(tableRow(m.alias(), r.threshold(), m.content().rows(), r.threshold())).append("\n");
            if (r.candidateDiffMerged() != r.allowedDiffCount()) {
                sb.append("\n> ").append(m.alias()).append(": 기준 거리 경계에 거리가 같은 다른 사건 쌍이 있어 ")
                        .append(r.allowedDiffCount()).append("쌍이 아니라 ").append(r.candidateDiffMerged())
                        .append("쌍이 들어갔다.\n\n");
            }
        }
        sb.append("\n");
    }

    // 한 행: 거리 이하인 쌍을 센다. 화재 칸도 같은 거리(전체 쌍으로 정한 거리)로 센다 — 화재만으로 거리를 다시 정하지 않는다
    private static String tableRow(String alias, double threshold, List<Row> rows, double at) {
        Predicate<Row> in = r -> r.distance() <= at;
        Predicate<Row> same = labelIs(Label.SAME);
        Predicate<Row> diff = labelIs(Label.DIFF);
        return "| " + alias + " | " + fmt(threshold) + " | " + outOf(rows, diff, in) + " | " + outOf(rows, same, in)
                + " | " + outOf(rows, same.and(Row::isFire), in) + " | " + outOf(rows, diff.and(Row::isFire), in) + " |";
    }

    // 모델 하나의 상세: 기준 거리를 정한 다른 사건 쌍 두 개와 기준선 대비 바뀐 같은 사건 쌍의 원문
    private static void appendDetail(StringBuilder sb, Input in, Model m) {
        sb.append("## ").append(m.alias()).append(" 상세\n\n");
        for (int i = 0; i < POINTS.length; i++) {
            Result r = compare(in, m, POINTS[i]);
            sb.append("### ").append(POINT_NAMES[i]).append(" — 기준 거리 ").append(fmt(r.threshold())).append("\n\n");
            sb.append("기준을 정한 다른 사건 쌍(거리 순 ").append(r.allowedDiffCount()).append("번째와 ")
                    .append(r.allowedDiffCount() + 1).append("번째, 한쪽이 없으면 하나만):\n");
            appendPairs(sb, r.thresholdPairs(), in, m);
            sb.append("\n기준선에서는 기준 밖, 후보에서는 기준 안으로 들어온 같은 사건 쌍 ").append(r.newlyInside().size()).append("쌍:\n");
            appendPairs(sb, r.newlyInside(), in, m);
            sb.append("\n기준선에서는 기준 안, 후보에서는 기준 밖으로 나간 같은 사건 쌍 ").append(r.newlyOutside().size()).append("쌍:\n");
            appendPairs(sb, r.newlyOutside(), in, m);
            sb.append("\n");
        }
    }

    private static void appendPairs(StringBuilder sb, List<PairKey> keys, Input in, Model m) {
        Map<PairKey, Double> distance = new HashMap<>();
        m.content().rows().forEach(r -> distance.put(new PairKey(r.alertA(), r.alertB()), r.distance()));
        for (PairKey k : keys) {
            sb.append("- 쌍 ").append(k.alertA()).append("-").append(k.alertB()).append(" (후보 거리 ")
                    .append(fmt(distance.get(k))).append(")\n");
            sb.append("  - 알림 ").append(k.alertA()).append(": ").append(in.messages().getOrDefault(k.alertA(), "(원문 없음)")).append("\n");
            sb.append("  - 알림 ").append(k.alertB()).append(": ").append(in.messages().getOrDefault(k.alertB(), "(원문 없음)")).append("\n");
        }
    }

    private static Result compare(Input in, Model m, double point) {
        return EmbeddingPairComparison.compare(toDistances(in.baseline()), toDistances(m.content()), point);
    }

    private static List<EmbeddingPairComparison.PairDistance> toDistances(Content c) {
        return c.rows().stream().map(Row::toPairDistance).toList();
    }

    private static Predicate<Row> labelIs(Label label) {
        return r -> r.label() == label;
    }

    private static long count(List<Row> rows, Predicate<Row> p) {
        return rows.stream().filter(p).count();
    }

    // "56쌍 중 1쌍" — 분모(조건에 맞는 전체 쌍)와 분자(그중 거리 이하)를 항상 함께 쓴다
    private static String outOf(List<Row> rows, Predicate<Row> group, Predicate<Row> inside) {
        return count(rows, group) + "쌍 중 " + count(rows, group.and(inside)) + "쌍";
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }
}
