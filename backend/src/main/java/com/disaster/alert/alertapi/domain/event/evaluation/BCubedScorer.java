package com.disaster.alert.alertapi.domain.event.evaluation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * 정답 사건 나눔과 시스템 이벤트 나눔을 알림 단위 B-cubed 로 비교한다.
 *
 * <p>알림 i 마다 정답 사건 집합 G(i), 시스템 이벤트 집합 S(i) 를 두고
 * 맞게 묶은 비율 = |S∩G| / |S|, 빠짐없이 묶은 비율 = |S∩G| / |G| 를 구해 알림 전체에서 평균한다.
 * G 와 S 는 모두 "점수 대상 알림" 으로 좁힌 집합이다 — 한쪽에만 빠진 알림이 한쪽 분모에만 남으면 점수가 흔들린다.
 *
 * <p>점수 대상은 분류 불가가 아닌 정답 알림 전부다. 시스템이 이벤트에 배정하지 못한(미배정) 알림은 빼지 않고,
 * 각자 "혼자인 시스템 이벤트" 로 계산한다. 못 묶은 알림을 점수에서 빼면 정답 사건 크기에서도 같이 빠져,
 * 시스템이 못 묶을수록 빠짐없이 묶은 비율이 오르는 왜곡이 생기기 때문이다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BCubedScorer {

    // 시스템 이벤트 식별자. solo=true 면 미배정 알림 한 건짜리 가짜 이벤트(id 는 알림 id)다.
    // solo 플래그를 키에 넣어, 실제 이벤트 id 와 같은 숫자여도 서로 다른 이벤트로 취급한다.
    private record EventRef(long id, boolean solo) {
    }

    // (정답 사건 키, 시스템 이벤트) 쌍 — 두 집합의 교집합 크기를 세는 키
    private record Cell(long goldKey, EventRef event) {
    }

    // 점수 대상 알림 한 건: 정답 사건 키와 (실제 또는 혼자인) 시스템 이벤트
    private record Target(long goldKey, EventRef event) {
    }

    /**
     * @param goldClusterOf 알림 id -> 정답 사건 키
     * @param unsure        분류 불가 알림 id (점수에서 제외)
     * @param systemEventOf 알림 id -> 시스템 이벤트 id. 이 맵에 없는 알림은 이벤트에 배정되지 않은 알림이며,
     *                      점수에는 혼자인 이벤트로 포함된다
     */
    public static BCubedResult score(Map<Long, Long> goldClusterOf, Set<Long> unsure,
                                     Map<Long, Long> systemEventOf) {
        // 1) 점수 대상을 가른다. 분류 불가는 사람도 판단을 못 한 알림이라 뺀다.
        //    미배정은 빼지 않고, 알림마다 혼자인 이벤트를 하나씩 줘서 서로 합쳐지지 않게 한다(건수만 따로 센다)
        //    분류 불가 건수는 unsure 집합 크기다 — 실제 정답 파일(BCubedLabelJson)은 unsure 알림을 goldClusterOf 에 넣지 않으므로
        //    "goldClusterOf 에 있는 것만" 세면 항상 0 이 된다. 분류 불가이면서 미배정인 알림은 분류 불가로만 센다
        int excludedUnsure = unsure.size();
        int unassigned = 0;
        List<Target> targets = new ArrayList<>();
        for (Map.Entry<Long, Long> gold : goldClusterOf.entrySet()) {
            long alertId = gold.getKey();
            if (unsure.contains(alertId)) {
                continue; // 이미 excludedUnsure 에 들어 있다
            }
            Long eventId = systemEventOf.get(alertId);
            if (eventId == null) {
                unassigned++;
                targets.add(new Target(gold.getValue(), new EventRef(alertId, true)));
            } else {
                targets.add(new Target(gold.getValue(), new EventRef(eventId, false)));
            }
        }

        // 2) 대상이 0 이면 평균을 정할 수 없다 — 0% 로 지어내지 않고 empty
        if (targets.isEmpty()) {
            return new BCubedResult(0, excludedUnsure, unassigned,
                    OptionalDouble.empty(), OptionalDouble.empty());
        }

        // 3) 알림마다 집합을 다시 만들면 O(N²) 라, 정답 사건별·이벤트별·(사건,이벤트) 쌍별 대상 수를 한 번씩만 센다
        Map<Long, Integer> goldSize = new HashMap<>();
        Map<EventRef, Integer> eventSize = new HashMap<>();
        Map<Cell, Integer> overlap = new HashMap<>();
        for (Target t : targets) {
            goldSize.merge(t.goldKey(), 1, Integer::sum);
            eventSize.merge(t.event(), 1, Integer::sum);
            overlap.merge(new Cell(t.goldKey(), t.event()), 1, Integer::sum);
        }

        // 4) 알림마다 두 비율(교집합 ÷ 이벤트 크기, 교집합 ÷ 정답 사건 크기)을 구해 더하고 평균한다
        double precisionSum = 0;
        double completenessSum = 0;
        for (Target t : targets) {
            int both = overlap.get(new Cell(t.goldKey(), t.event()));
            precisionSum += (double) both / eventSize.get(t.event());
            completenessSum += (double) both / goldSize.get(t.goldKey());
        }
        int n = targets.size();
        return new BCubedResult(n, excludedUnsure, unassigned,
                OptionalDouble.of(precisionSum / n * 100), OptionalDouble.of(completenessSum / n * 100));
    }
}
