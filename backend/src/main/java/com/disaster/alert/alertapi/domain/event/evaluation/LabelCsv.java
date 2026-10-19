package com.disaster.alert.alertapi.domain.event.evaluation;

import java.util.List;

/**
 * 라벨 CSV 텍스트를 쌍 목록으로 바꾼다. 형식 오류는 "N번째 줄" 을 담은 IllegalArgumentException.
 */
// Red 단계 뼈대 — 구현은 Green 에서
public final class LabelCsv {

    public enum Label { SAME, DIFF, UNSURE }

    /** 라벨된 알림 쌍. stratum·sub_type 은 읽고 버리므로 담지 않는다. */
    public record LabeledPair(long alertA, long alertB, Label label) {
    }

    private LabelCsv() {
    }

    public static List<LabeledPair> parse(String csvText) {
        return List.of();
    }
}
