package com.disaster.alert.alertapi.domain.event.evaluation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 클러스터링 평가용 정답 라벨 CSV 를 파싱하는 순수 로직 클래스.
 * 라벨 데이터가 평가 결과의 근거이므로, 형식이 조금이라도 어긋나면 줄 번호(헤더=1)와 함께 즉시 거부한다.
 */
public final class EvaluationLabelParser {

    private static final String HEADER = "alert_a_id,alert_b_id,label,stratum,sub_type";
    private static final int COLUMN_COUNT = 5;
    private static final char BOM = '﻿';

    private EvaluationLabelParser() {
    }

    public static List<EvaluationLabelRecord> parse(String csvText) {
        if (csvText == null || csvText.isBlank()) {
            throw new IllegalArgumentException("라벨 CSV 가 비어 있습니다");
        }
        String text = csvText.charAt(0) == BOM ? csvText.substring(1) : csvText;
        // limit -1: 끝의 빈 필드("1,2,SAME,GENERAL,")를 버리지 않아 빈 sub_type 을 감지할 수 있다.
        String[] lines = text.split("\\r?\\n", -1);

        if (!HEADER.equals(lines[0].trim())) {
            throw new IllegalArgumentException("1번째 줄: 헤더가 '" + HEADER + "' 와 다릅니다");
        }

        List<EvaluationLabelRecord> records = new ArrayList<>();
        Map<String, Integer> seenPairs = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            int lineNo = i + 1;
            EvaluationLabelRecord record = parseRow(lines[i], lineNo);
            checkDuplicate(record, lineNo, seenPairs);
            records.add(record);
        }
        return records;
    }

    private static EvaluationLabelRecord parseRow(String line, int lineNo) {
        String[] cols = line.split(",", -1);
        if (cols.length != COLUMN_COUNT) {
            throw error(lineNo, "컬럼 수가 " + cols.length + "개입니다 (필요: " + COLUMN_COUNT + ")");
        }
        long a = parseId(cols[0], "alert_a_id", lineNo);
        long b = parseId(cols[1], "alert_b_id", lineNo);
        if (a == b) {
            throw error(lineNo, "alert_a_id 와 alert_b_id 가 같습니다: " + a);
        }
        EvaluationLabelType label = parseEnum(EvaluationLabelType.class, cols[2], "label", lineNo);
        EvaluationStratum stratum = parseEnum(EvaluationStratum.class, cols[3], "stratum", lineNo);
        String subType = cols[4].trim();
        if (subType.isEmpty()) {
            throw error(lineNo, "sub_type 이 비어 있습니다");
        }
        return new EvaluationLabelRecord(a, b, label, stratum, subType);
    }

    private static long parseId(String raw, String column, int lineNo) {
        long id;
        try {
            id = Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw error(lineNo, column + " 가 숫자가 아닙니다: '" + raw.trim() + "'");
        }
        if (id <= 0) {
            throw error(lineNo, column + " 는 1 이상이어야 합니다: " + id);
        }
        return id;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String column, int lineNo) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw error(lineNo, "알 수 없는 " + column + ": '" + raw.trim() + "'");
        }
    }

    // (a,b) 와 (b,a) 는 같은 쌍이다 — 뒤집힌 중복이 서로 다른 정답으로 들어가면 평가가 모순된다.
    private static void checkDuplicate(EvaluationLabelRecord r, int lineNo, Map<String, Integer> seenPairs) {
        String key = Math.min(r.alertAId(), r.alertBId()) + ":" + Math.max(r.alertAId(), r.alertBId());
        Integer firstLine = seenPairs.putIfAbsent(key, lineNo);
        if (firstLine != null) {
            throw error(lineNo, "중복된 쌍입니다 (" + firstLine + "번째 줄과 " + lineNo + "번째 줄): " + key);
        }
    }

    private static IllegalArgumentException error(int lineNo, String message) {
        return new IllegalArgumentException(lineNo + "번째 줄: " + message);
    }
}
