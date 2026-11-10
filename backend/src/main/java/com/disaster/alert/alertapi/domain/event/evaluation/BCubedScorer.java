package com.disaster.alert.alertapi.domain.event.evaluation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * 정답 사건 나눔과 시스템 이벤트 나눔을 알림 단위 B-cubed 로 비교한다.
 */
// Red 단계 뼈대 — 구현은 Green 에서
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BCubedScorer {

    /**
     * @param goldClusterOf 알림 id -> 정답 사건 키
     * @param unsure        분류 불가 알림 id (점수에서 제외)
     * @param systemEventOf 알림 id -> 시스템 이벤트 id. 이 맵에 없는 알림은 이벤트에 배정되지 않은 알림이다
     */
    public static BCubedResult score(Map<Long, Long> goldClusterOf, Set<Long> unsure,
                                     Map<Long, Long> systemEventOf) {
        return new BCubedResult(0, 0, 0, OptionalDouble.empty(), OptionalDouble.empty());
    }
}
