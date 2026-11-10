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

    // 시스템이 이벤트에 못 넣은(미배정) 알림은 "혼자인 시스템 이벤트" 로 취급해 점수에 포함해야 한다.
    // 점수에서 빼면 정답 사건 크기에서도 같이 빠져, 시스템이 못 묶을수록 "빠짐없이 묶은 비율" 이 오른다(놓친 것이 가려진다).
    // Red 단계: 뼈대는 미배정을 아직 점수에서 빼므로 scored=5, 빠짐없이 묶은 비율 100% 가 나와 "scored 가 10" 기대에서 실패한다.
    @Test
    void 미배정_알림은_혼자인_이벤트로_점수에_포함된다() {
        // 준비: 정답은 사건 하나(키 1)에 알림 1~10 이 모두 들어 있다
        Map<Long, Long> gold = new HashMap<>();
        for (long id = 1; id <= 10; id++) {
            gold.put(id, 1L);
        }
        // 시스템은 1~5 만 이벤트 E(id 10)에 묶었고 6~10 은 맵에 없다(미배정)
        Map<Long, Long> system = new HashMap<>();
        for (long id = 1; id <= 5; id++) {
            system.put(id, 10L);
        }

        // 실행
        BCubedResult result = BCubedScorer.score(gold, Set.of(), system);

        // 검증: 10건 모두 점수 대상이고, 미배정 5건은 리포트에 알리려고 따로 센다
        assertThat(result.scored()).isEqualTo(10);
        assertThat(result.unassigned()).isEqualTo(5);
        // 맞게 묶은 비율: 1~5 는 E 안이 전부 같은 사건이라 5/5, 6~10 은 각자 혼자라 1/1 -> 100%.
        assertThat(result.precisionRate()).hasValueCloseTo(100.0, within(0.01));
        // 빠짐없이 묶은 비율: 1~5 는 같은 사건 10개 중 5개만 같은 이벤트(5/10), 6~10 은 자기 자신뿐(1/10)
        // -> (5×0.5 + 5×0.1)/10 = 30%. 미배정끼리 한 이벤트로 합쳐졌다면 6~10 이 5/10 이 돼 50% 가 되므로, 30% 가 "합치지 않음" 을 고정한다.
        assertThat(result.completenessRate()).hasValueCloseTo(30.0, within(0.01));
    }

    // 분류 불가 알림은 사람도 판단을 못 한 것이라 "정답 사건 집합" 과 "시스템 이벤트 집합" 양쪽에서 모두 빠져야 한다.
    // 한쪽에만 남으면 같은 알림이 한쪽 분모에서만 세어져, 판단 못 한 알림 때문에 점수가 흔들린다.
    // 위 손계산 예시에 알림 7(분류 불가인데 시스템은 Y 에 넣음)을 더해도 비율은 그대로여야 한다. 7 이 Y 의 분모에 남으면 Y 가 4개로 보여 맞게 묶은 비율이 내려간다.
    // 알림 9 는 분류 불가이면서 미배정이다 — 이 경우 분류 불가로만 세고 미배정으로는 세지 않는다(한 알림을 두 번 세지 않으려고).
    // Red 단계: 뼈대 자체는 이미 분류 불가를 제외하므로 이 검증은 통과한다(미배정 건수 이름 변경에 따른 컴파일 정합 확인 겸용).
    @Test
    void 분류_불가는_양쪽_집합에서_빠지고_미배정과_겹치면_분류_불가로만_센다() {
        // 준비: 정답에 7(분류 불가, 시스템은 Y 에 넣음)과 9(분류 불가, 시스템은 이벤트에 못 넣음)를 추가
        Map<Long, Long> gold = new HashMap<>(GOLD);
        gold.put(7L, 4L);
        gold.put(9L, 5L);
        Map<Long, Long> system = new HashMap<>(SYSTEM);
        system.put(7L, 20L);

        // 실행
        BCubedResult result = BCubedScorer.score(gold, Set.of(7L, 9L), system);

        // 검증: 분류 불가 2건만 세고 미배정은 0, 점수 대상은 원래 예시의 6건, 비율은 손계산 값 그대로
        assertThat(result.excludedUnsure()).isEqualTo(2);
        assertThat(result.unassigned()).isZero();
        assertThat(result.scored()).isEqualTo(6);
        assertThat(result.precisionRate()).hasValueCloseTo(7.0 / 9 * 100, within(0.01));
        assertThat(result.completenessRate()).hasValueCloseTo(75.0, within(0.01));
    }
}
