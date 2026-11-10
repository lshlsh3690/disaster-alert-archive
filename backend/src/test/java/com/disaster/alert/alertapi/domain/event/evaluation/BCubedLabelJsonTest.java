package com.disaster.alert.alertapi.domain.event.evaluation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BCubedLabelJsonTest {

    // 정답 파일의 세 갈래(events / solo / unsure)를 점수 계산기가 쓰기 좋은 "알림 -> 사건 키" 맵으로 편다.
    // 같은 사건 알림은 같은 키, 혼자인 알림은 서로 다른 키여야 한다 — solo 둘이 같은 키를 받으면
    // 계산기가 서로 무관한 두 알림을 같은 사건으로 보고 점수가 틀어진다.
    // Red 단계: 뼈대는 빈 맵·빈 집합을 돌려주므로 "알림 id 가 맵에 있어야 한다" 기대에서 실패한다.
    @Test
    void events와_solo와_unsure를_알림_단위_정답으로_펼친다() {
        // 준비: 사건 7번(알림 1,2), 사건 9번(알림 3), 혼자인 알림 4와 5, 분류 불가 6
        String json = """
                {"window":{"from":"2026-05-01","to":"2026-05-31"},"exportedAt":"2026-11-10T09:00:00",
                 "events":[{"id":7,"alertIds":[1,2]},{"id":9,"alertIds":[3]}],
                 "solo":[4,5],
                 "unsure":[6],
                 "unreviewed":[]}
                """;

        // 실행
        BCubedGold gold = BCubedLabelJson.parse(json);

        // 검증 1: 같은 사건이면 같은 키, 다른 사건이면 다른 키 (키 값 자체는 event id 라 7, 9)
        Map<Long, Long> clusterOf = gold.clusterOf();
        assertThat(clusterOf).containsEntry(1L, 7L).containsEntry(2L, 7L).containsEntry(3L, 9L);

        // 검증 2: 혼자인 알림 4,5 는 모두 맵에 있고 서로 다르며 events 의 키와도 겹치지 않는다
        assertThat(clusterOf).containsKeys(4L, 5L);
        assertThat(clusterOf.get(4L)).isNotEqualTo(clusterOf.get(5L)).isNotIn(7L, 9L);
        assertThat(clusterOf.get(5L)).isNotIn(7L, 9L);

        // 검증 3: 분류 불가 알림은 집합으로 그대로 나온다(점수에서 빼는 건 계산기 몫)
        assertThat(gold.unsure()).containsExactly(6L);
    }

    // 아직 검토하지 않은 알림이 남은 정답으로 점수를 내면 숫자가 조용히 틀어진다(안 본 알림이 점수에서 빠지므로).
    // 그래서 unreviewed 가 비어 있지 않으면 즉시 실패한다.
    // Red 단계: 뼈대는 아무것도 던지지 않으므로 "예외가 없다" 로 실패한다.
    @Test
    void unreviewed가_남아_있으면_예외() {
        // 준비: 나머지는 멀쩡하지만 검토 안 된 알림 99 가 하나 남아 있다
        String json = """
                {"events":[{"id":1,"alertIds":[1,2]}],"solo":[3],"unsure":[],"unreviewed":[99]}
                """;

        // 실행 + 검증
        assertThatThrownBy(() -> BCubedLabelJson.parse(json))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
