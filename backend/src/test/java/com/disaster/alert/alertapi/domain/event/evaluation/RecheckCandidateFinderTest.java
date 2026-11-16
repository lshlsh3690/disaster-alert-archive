package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.AgencyKind;
import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.AlertFacts;
import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.PairFacts;
import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.SigunguRelation;
import com.disaster.alert.alertapi.domain.event.evaluation.RecheckCandidateFinder.PairInput;
import com.disaster.alert.alertapi.domain.event.evaluation.RecheckCandidateFinder.Rule;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 재확인 후보 추출 테스트 — 새 기준(D·E·G·I)의 조건에 걸리는 쌍만 사람이 다시 보도록 골라내는지 확인한다.
 * 판정 사실(AlertFacts·PairFacts)은 계산기를 돌리지 않고 record 로 직접 만들어 넣는다. 순수 단위 테스트다.
 */
class RecheckCandidateFinderTest {

    private final RecheckCandidateFinder finder = new RecheckCandidateFinder();

    // 아무 규칙에도 안 걸리는 "평범한" 알림: 시군구 기관(산청군) 발신, 수신 시군구 1개, 옛 인천 구 아님.
    private static AlertFacts plain() {
        return new AlertFacts(Optional.of("산청군"), AgencyKind.SIGUNGU, Optional.of("48860"), 1);
    }

    // 평범한 쌍의 견준 사실: 같은 시군구, 시설 이름 겹침 없음(G 안 걸림).
    private static PairFacts plainPair() {
        return new PairFacts(SigunguRelation.SAME, Duration.ofHours(1), List.of());
    }

    /**
     * 규칙 D: 한쪽이라도 여러 시군구로 보낸 문자(다지역 발송)면 같은 사건인지 다시 봐야 한다 — 새 기준에서
     * 다지역 문자는 지역이 달라도 한 사건일 수 있어서, 예전 기준으로 매긴 라벨이 틀렸을 수 있다.
     * Red: 뼈대는 요청한 규칙마다 빈 목록만 돌려주므로 D 목록에 쌍이 없어 실패한다.
     */
    @Test
    void 한쪽_수신_시군구가_2개_이상인_쌍은_D_후보다() {
        // Given: 알림 A 는 수신 시군구가 3개(다지역 발송), B 는 평범한 알림. 한쪽만 걸려도 쌍이 걸린다
        AlertFacts multiRegion = new AlertFacts(Optional.of("산청군"), AgencyKind.SIGUNGU, Optional.of("48860"), 3);
        PairInput pair = new PairInput(1L, 2L, multiRegion, plain(), plainPair());

        // When: D 규칙만 켜고 후보를 찾는다
        Map<Rule, List<PairInput>> result = finder.find(EnumSet.of(Rule.D), List.of(pair));

        // Then: 그 쌍이 D 후보로 잡힌다(getOrDefault — 키가 없어도 NPE 가 아니라 단언에서 실패하게 한다)
        assertThat(result.getOrDefault(Rule.D, List.of())).containsExactly(pair);
    }

    /**
     * 규칙 I: 발신 기관이 시군구가 아니면(철도·경찰청·도청 등) 본문 속 장소가 어느 시군구인지 기관 이름으로는 알 수 없어
     * 장소 판단이 달라진다 — 그런 쌍은 라벨을 다시 봐야 한다.
     * Red: 뼈대는 빈 목록만 돌려주므로 I 목록에 쌍이 없어 실패한다.
     */
    @Test
    void 한쪽_발신_기관이_시군구가_아니면_I_후보다() {
        // Given: 알림 B 의 발신 기관이 철도공사(시군구 아님, 시군구 모름). A 는 평범한 알림
        AlertFacts railway = new AlertFacts(Optional.of("한국철도공사"), AgencyKind.NON_SIGUNGU, Optional.empty(), 1);
        PairInput pair = new PairInput(1L, 2L, plain(), railway, plainPair());

        // When: I 규칙만 켜고 후보를 찾는다
        Map<Rule, List<PairInput>> result = finder.find(EnumSet.of(Rule.I), List.of(pair));

        // Then: 한쪽(B)만 시군구가 아니어도 그 쌍이 I 후보로 잡힌다
        assertThat(result.getOrDefault(Rule.I, List.of())).containsExactly(pair);
    }

    /**
     * 걸리지 않는 쌍은 사람이 다시 볼 이유가 없다 — 모든 규칙을 켜도 어느 목록에도 들어가면 안 된다.
     * 후보가 과하게 잡히면 사람이 볼 양이 늘어 재확인의 의미가 흐려진다.
     * 회귀 고정용: 뼈대(빈 목록)에서도 통과하는 케이스라 Red 에서는 실패하지 않는다. 구현이 지나치게 넓게 잡으면 깨진다.
     */
    @Test
    void 어느_조건에도_안_걸리는_쌍은_어느_규칙_목록에도_없다() {
        // Given: 둘 다 시군구 기관·수신 시군구 1개·옛 인천 구 아님·겹치는 시설 없음
        PairInput pair = new PairInput(1L, 2L, plain(), plain(), plainPair());

        // When: D·E·G·I 모두 켜고 후보를 찾는다
        Map<Rule, List<PairInput>> result = finder.find(EnumSet.allOf(Rule.class), List.of(pair));

        // Then: 켠 규칙 어느 목록에도 이 쌍이 없다
        for (Rule rule : Rule.values()) {
            assertThat(result.getOrDefault(rule, List.of())).as("규칙 %s 후보", rule).isEmpty();
        }
    }
}
