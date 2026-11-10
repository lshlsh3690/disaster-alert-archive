package com.disaster.alert.alertapi.domain.event.evaluation;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BCubedScorerTest {

    // 정답 사건 A(키 1)={1,2,3,4}, B(키 2)={5,6}
    private static final Map<Long, Long> GOLD = Map.of(
            1L, 1L, 2L, 1L, 3L, 1L, 4L, 1L,
            5L, 2L, 6L, 2L);

    // 시스템 이벤트 X(id 10)={1,2,3}, Y(id 20)={4,5,6} — 알림 4 가 엉뚱하게 B 쪽으로 넘어간 상황
    private static final Map<Long, Long> SYSTEM = Map.of(
            1L, 10L, 2L, 10L, 3L, 10L,
            4L, 20L, 5L, 20L, 6L, 20L);

    // 손으로 푼 예시로 계산 방식을 고정한다. 알림마다 (맞게 묶은 비율, 빠짐없이 묶은 비율)은
    //   1·2·3 -> (3/3, 3/4) = (1.0, 0.75)   X 는 전부 A 에서 왔지만 A 의 4 를 놓침
    //   4     -> (1/3, 1/4)                  Y={4,5,6} 중 같은 사건은 자기 자신뿐, A 는 4개 중 1개만 같은 이벤트
    //   5·6   -> (2/3, 2/2) = (0.667, 1.0)   Y 안에 4 가 섞였지만 B 는 전부 모음
    // 평균: 맞게 묶은 비율 (3+1/3+4/3)/6 = 7/9 = 77.78%, 빠짐없이 묶은 비율 (2.25+0.25+2)/6 = 75%,
    // 점수 = 조화평균 2·(7/9)(3/4)/((7/9)+(3/4)) = 42/55 = 76.36%.
    // Red 단계: 뼈대는 비율을 empty 로 돌려주므로 "값이 있어야 한다" 기대에서 실패한다.
    @Test
    void 손으로_푼_예시의_두_비율과_점수가_나온다() {
        // 실행: 분류 불가·미배정 알림이 없는 가장 단순한 입력
        BCubedResult result = BCubedScorer.score(GOLD, Set.of(), SYSTEM);

        // 검증: 6건 모두 점수 대상이고, 위에서 손으로 계산한 값과 같다(허용 오차 0.01%p)
        assertThat(result.scored()).isEqualTo(6);
        assertThat(result.precisionRate()).hasValueCloseTo(7.0 / 9 * 100, within(0.01));
        assertThat(result.completenessRate()).hasValueCloseTo(75.0, within(0.01));
        assertThat(result.score()).hasValueCloseTo(42.0 / 55 * 100, within(0.01));
    }

    // 분류 불가·미배정 알림은 "정답 사건 집합" 과 "시스템 이벤트 집합" 양쪽에서 모두 빠져야 한다.
    // 한쪽에만 남으면 같은 알림이 한쪽 분모에서만 세어져, 사람이 판단을 못 한 알림 때문에 점수가 흔들린다.
    // 위 예시에 알림 7(분류 불가인데 시스템은 Y 에 넣음)과 알림 8(정답에선 혼자인데 시스템이 이벤트에 못 넣음)을
    // 더해도 비율은 그대로여야 한다. 7 이 Y 의 분모에 남으면 Y 가 4개로 보여 맞게 묶은 비율이 내려간다.
    // Red 단계: 뼈대는 scored=0 과 empty 를 돌려주므로 "scored 가 6" 기대에서 실패한다.
    @Test
    void 분류_불가와_미배정_알림은_양쪽_집합에서_빠져_비율이_그대로다() {
        // 준비: 정답엔 7(분류 불가로 표시)과 8(혼자, 사건 키 3)을 추가.
        // 7 은 정답 맵에도 있지만 unsure 에 들었으므로 제외돼야 한다.
        Map<Long, Long> gold = new HashMap<>(GOLD);
        gold.put(7L, 4L);
        gold.put(8L, 3L);
        // 시스템은 7 을 Y 에 넣었고, 8 은 어느 이벤트에도 넣지 않았다(맵에 없음 = 미배정)
        Map<Long, Long> system = new HashMap<>(SYSTEM);
        system.put(7L, 20L);

        // 실행
        BCubedResult result = BCubedScorer.score(gold, Set.of(7L), system);

        // 검증: 제외 건수가 각각 1, 점수 대상은 원래 예시의 6건, 비율은 손계산 값 그대로
        assertThat(result.excludedUnsure()).isEqualTo(1);
        assertThat(result.excludedUnassigned()).isEqualTo(1);
        assertThat(result.scored()).isEqualTo(6);
        assertThat(result.precisionRate()).hasValueCloseTo(7.0 / 9 * 100, within(0.01));
        assertThat(result.completenessRate()).hasValueCloseTo(75.0, within(0.01));
    }
}
