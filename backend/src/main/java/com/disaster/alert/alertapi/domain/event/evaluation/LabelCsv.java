package com.disaster.alert.alertapi.domain.event.evaluation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 라벨 CSV 텍스트를 쌍 목록으로 바꾼다. 형식 오류는 "N번째 줄" 을 담은 IllegalArgumentException.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LabelCsv {

    public enum Label { SAME, DIFF, UNSURE }

    /** 라벨된 알림 쌍. stratum·sub_type 은 읽고 버리므로 담지 않는다. */
    public record LabeledPair(long alertA, long alertB, Label label) {
    }

    private static final String HEADER = "alert_a_id,alert_b_id,label,stratum,sub_type";
    private static final int COLUMN_COUNT = 5;

    /**
     * CSV 전체를 읽어 쌍 목록을 만든다. 한 줄이라도 틀리면 건너뛰지 않고 즉시 예외 —
     * 조용히 버리면 평가 숫자가 슬그머니 달라지기 때문이다.
     */
    public static List<LabeledPair> parse(String csvText) {
        // 1) BOM 제거 — 엑셀 저장본은 맨 앞에 BOM 이 붙어 헤더 비교가 어긋난다
        String text = csvText.startsWith("﻿") ? csvText.substring(1) : csvText;
        // 2) 끝의 빈 문자열도 남겨 물리적 줄 번호(헤더=1)를 그대로 유지한다
        String[] lines = text.split("\r?\n", -1);

        if (!lines[0].trim().equals(HEADER)) {
            throw error(1, "헤더가 '" + HEADER + "' 와 다릅니다: " + lines[0]);
        }

        List<LabeledPair> pairs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 1; i < lines.length; i++) {
            // 3) 빈 줄은 오류가 아니다 — 건너뛰되 줄 번호는 인덱스(i+1)로 계속 센다
            if (lines[i].isBlank()) {
                continue;
            }
            pairs.add(parseLine(lines[i], i + 1, seen));
        }
        return pairs;
    }

    private static LabeledPair parseLine(String line, int lineNo, Set<String> seen) {
        // -1: 끝의 빈 컬럼(sub_type 이 비어 있음)도 컬럼 수에 포함해 센다
        String[] cols = line.split(",", -1);
        if (cols.length != COLUMN_COUNT) {
            throw error(lineNo, "컬럼이 " + COLUMN_COUNT + "개여야 하는데 " + cols.length + "개입니다: " + line);
        }
        long a = parseId(cols[0], lineNo);
        long b = parseId(cols[1], lineNo);
        if (a == b) {
            throw error(lineNo, "같은 알림끼리의 쌍입니다: " + a);
        }
        Label label = parseLabel(cols[2], lineNo);

        // 4) (a,b)/(b,a) 를 같은 키로 만들어 중복을 잡는다 — 뒤쪽 줄이 지목된다
        String key = Math.min(a, b) + ":" + Math.max(a, b);
        if (!seen.add(key)) {
            throw error(lineNo, "이미 나온 쌍입니다(순서 무관): " + a + "," + b);
        }
        return new LabeledPair(a, b, label);
    }

    private static long parseId(String raw, int lineNo) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw error(lineNo, "알림 id 가 숫자가 아닙니다: " + raw);
        }
    }

    private static Label parseLabel(String raw, int lineNo) {
        try {
            return Label.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw error(lineNo, "알 수 없는 label 입니다(SAME/DIFF/UNSURE): " + raw);
        }
    }

    private static IllegalArgumentException error(int lineNo, String reason) {
        return new IllegalArgumentException(lineNo + "번째 줄: " + reason);
    }
}
