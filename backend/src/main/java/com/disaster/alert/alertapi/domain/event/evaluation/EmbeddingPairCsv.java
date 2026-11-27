package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.model.EvaluationLabel.Relation;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 모델별 거리 파일(embedding-pairs-&lt;별칭&gt;.csv)의 쓰기·읽기(specs/012 data-model 2절, JPA·Spring import 없음).
 * 머리의 {@code # 키=값} 줄에 모델 이름·주소·차원·실행 시각·토큰을 남기고, 그 뒤가 쌍별 거리 표다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EmbeddingPairCsv {

    public static final String COLUMNS = "alert_a,alert_b,label,type,relation,distance";
    static final String SAME_SIGUNGU_TEXT = "같은 시군구";
    static final String ADJACENT_SIGUNGU_TEXT = "이웃 시군구";

    /** 거리 파일 한 줄. type 은 같은 유형이면 그 유형, 다르면 가나다순으로 "/" 로 이은 것이다. */
    public record Row(long alertA, long alertB, Label label, String type, String relation, double distance) {

        EmbeddingPairComparison.PairDistance toPairDistance() {
            return new EmbeddingPairComparison.PairDistance(alertA, alertB, label, distance);
        }

        /** 두 알림이 모두 화재인 쌍(유형 칸이 그냥 "화재" 일 때만 해당). */
        boolean isFire() {
            return "화재".equals(type);
        }
    }

    /** 파일 내용: 머리 키=값(쓴 순서 유지)과 쌍별 거리. */
    public record Content(Map<String, String> header, List<Row> rows) {
    }

    /** 두 유형을 한 칸으로: 같으면 그 유형, 다르면 가나다순으로 "/" 로 잇는다(같은 쌍은 순서와 상관없이 같은 문자열). */
    public static String typeText(String typeA, String typeB) {
        String a = typeA == null ? "" : typeA;
        String b = typeB == null ? "" : typeB;
        if (a.equals(b)) {
            return a;
        }
        return a.compareTo(b) < 0 ? a + "/" + b : b + "/" + a;
    }

    public static String relationText(Relation relation) {
        return relation == Relation.SAME_SIGUNGU ? SAME_SIGUNGU_TEXT : ADJACENT_SIGUNGU_TEXT;
    }

    /** 머리 줄과 거리 표를 파일 내용으로 만든다. 쌍은 (alert_a, alert_b) 순으로 정렬해 매번 같은 파일이 나오게 한다. */
    public static String format(Map<String, String> header, List<Row> rows) {
        StringBuilder sb = new StringBuilder();
        header.forEach((k, v) -> sb.append("# ").append(k).append('=').append(v.replace('\n', ' ')).append('\n'));
        sb.append(COLUMNS).append('\n');
        rows.stream()
                .sorted(Comparator.comparingLong(Row::alertA).thenComparingLong(Row::alertB))
                .forEach(r -> sb.append(r.alertA()).append(',').append(r.alertB()).append(',').append(r.label())
                        .append(',').append(r.type().replace(',', ' ')).append(',').append(r.relation())
                        .append(',').append(r.distance()).append('\n'));
        return sb.toString();
    }

    /** {@link #format} 의 거울. 열 머리가 다르거나 줄이 깨져 있으면 어디서 깨졌는지 알리며 멈춘다. */
    public static Content parse(String text) {
        Map<String, String> header = new LinkedHashMap<>();
        List<Row> rows = new ArrayList<>();
        boolean sawColumns = false;
        for (String line : text.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("# ")) {
                int eq = line.indexOf('=');
                header.put(eq < 0 ? line.substring(2) : line.substring(2, eq), eq < 0 ? "" : line.substring(eq + 1));
            } else if (!sawColumns) {
                if (!COLUMNS.equals(line)) {
                    throw new IllegalArgumentException("거리 파일 열 머리가 다릅니다: " + line);
                }
                sawColumns = true;
            } else {
                rows.add(parseRow(line));
            }
        }
        if (!sawColumns) {
            throw new IllegalArgumentException("거리 파일에 열 머리(" + COLUMNS + ")가 없습니다");
        }
        return new Content(header, rows);
    }

    private static Row parseRow(String line) {
        String[] f = line.split(",", -1);
        if (f.length != 6) {
            throw new IllegalArgumentException("거리 파일 줄 형식이 다릅니다: " + line);
        }
        return new Row(Long.parseLong(f[0]), Long.parseLong(f[1]), Label.valueOf(f[2]), f[3], f[4], Double.parseDouble(f[5]));
    }
}
