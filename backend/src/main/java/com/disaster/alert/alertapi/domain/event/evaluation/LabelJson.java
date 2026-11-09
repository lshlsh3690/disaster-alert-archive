package com.disaster.alert.alertapi.domain.event.evaluation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 라벨 JSON 텍스트를 쌍 목록으로 바꾼다. 형식 오류는 "N번째 항목" 을 담은 IllegalArgumentException.
 */
// Red 단계 뼈대 — 구현은 Green 에서
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LabelJson {

    public enum Label { SAME, DIFF, UNSURE }

    /** 라벨된 알림 쌍. 사람이 보려는 필드(원문·시각 등)는 읽고 버리므로 담지 않는다. */
    public record LabeledPair(long alertA, long alertB, Label label) {
    }

    public static List<LabeledPair> parse(String jsonText) {
        return List.of();
    }
}
