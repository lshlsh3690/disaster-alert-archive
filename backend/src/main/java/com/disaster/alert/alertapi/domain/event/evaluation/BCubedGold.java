package com.disaster.alert.alertapi.domain.event.evaluation;

import java.util.Map;
import java.util.Set;

/**
 * 알림 단위 B-cubed 평가의 정답. 사람이 구간 안 알림을 사건별로 나눈 결과를 읽어 온 값이다.
 *
 * @param clusterOf 알림 id -> 정답 사건 키. events 의 알림은 그 event id 를, 혼자인(solo) 알림은 서로 다른 고유 키를 갖는다
 * @param unsure    분류 불가 알림 id. 점수에서 제외한다
 */
public record BCubedGold(Map<Long, Long> clusterOf, Set<Long> unsure) {
}
