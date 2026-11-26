package com.disaster.alert.alertapi.domain.event.evaluation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 평가에 쓰는 라벨 값과 라벨된 쌍. 이름과 달리 JSON 을 읽지 않는다 — 라벨 JSON 파일 읽기는 2026-11-24 에 없앴고
 * 라벨은 DB(evaluation_pair_label·clustering_pair_label)에서만 읽는다. 36개 파일이 이 두 타입을 써서 이름은 그대로 둔다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LabelJson {

    public enum Label { SAME, DIFF, UNSURE }

    /** 라벨된 알림 쌍. 사람이 보려는 필드(원문·시각 등)는 담지 않는다. */
    public record LabeledPair(long alertA, long alertB, Label label) {
    }
}
