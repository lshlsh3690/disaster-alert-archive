package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.AgencyKind;
import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.AlertFacts;
import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.AlertInput;
import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.PairFacts;
import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.SigunguRelation;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 판정 사실 계산기 테스트 — 판정(같은/다른 사건)이 아니라 "어느 시군구 문자인가"와 "두 문자의 장소 관계"만 확인한다.
 * DB·Spring 없이 도는 순수 단위 테스트다. 시군구 이름 표와 이웃 표는 아래처럼 생성자로 직접 넣는다.
 */
class AlertPlaceFactsTest {

    // 시군구 이름 → 식별자 표. 코드는 실제 법정동 시군구 코드(앞 5자리)다.
    private static final String SANCHEONG = "48860";
    private static final String HAMYANG = "48870";
    // 옛 인천 서구는 개편 뒤 검단구·서해구로 나뉘었다 — 옛 서구 코드는 폐지(is_active=false)로 DB 에 남아 있다.
    private static final String OLD_SEO = "28260";
    private static final String GEOMDAN = "28290";
    private static final String SEOHAE = "28275";
    private static final String SEJONG = "36110";

    private static final Map<String, String> SIGUNGU_ID_BY_NAME = Map.of(
            "산청군", SANCHEONG,
            "함양군", HAMYANG,
            // 키는 시도 약칭 + 구 이름이다 — 서구는 부산·대구·대전에도 있어 "서구"만으로는 모호하다.
            "인천서구", OLD_SEO,
            "검단구", GEOMDAN,
            "서해구", SEOHAE,
            "세종시", SEJONG);

    // 이웃 표: 산청과 함양은 서로 이웃(region_adjacency 는 양방향 행으로 들어 있다고 보고 양쪽을 다 둔다).
    private static final Map<String, Set<String>> ADJACENT_IDS = Map.of(
            SANCHEONG, Set.of(HAMYANG),
            HAMYANG, Set.of(SANCHEONG));

    private final AlertPlaceFacts facts = new AlertPlaceFacts(SIGUNGU_ID_BY_NAME, ADJACENT_IDS);

    private static AlertInput alert(String body, LocalDateTime sentAt, String type, String... receivedCodes) {
        return new AlertInput(body, sentAt, type, List.of(receivedCodes));
    }

    // D: 발신 기관이 시군구 소속이면 부서 이름이 붙어도 시군구 기관이고, 그 시군구의 문자로 본다.
    // 부서명("상하수도과")까지 통째로 시군구 이름 표에서 찾으려 하면 못 찾아 "모름"이 되는 사고를 막는 결정이다.
    @Test
    void 시군구_부서_이름이_붙은_기관도_시군구_기관이고_그_시군구의_문자다() {
        // 준비: 본문 끝 [ ] 안이 "산청군" + 부서명. 수신 지역은 산청 한 곳
        AlertInput input = alert("신안면 일부 지역 단수 중입니다. 양해 바랍니다. [산청군상하수도과]",
                LocalDateTime.of(2025, 8, 6, 10, 0), "수도", SANCHEONG);

        // 실행: 문자 하나에서 사실을 뽑는다
        AlertFacts result = facts.analyze(input);

        // 검증: 발신 기관은 [ ] 안 문자열 그대로, 종류는 시군구 기관, 이 문자의 시군구는 산청군
        assertThat(result.agencyKind()).isEqualTo(AgencyKind.SIGUNGU);
        assertThat(result.senderAgency()).contains("산청군상하수도과");
        assertThat(result.sigunguId()).contains(SANCHEONG);
    }

    // I: 철도·관리본부처럼 시군구가 아닌 기관은 기관 이름만으로 시군구를 정할 수 없다.
    // 문자 속 장소는 규칙으로 안정적으로 못 뽑으므로 억지로 추정하지 않고 "모름"으로 두어 판단을 LLM 에 넘긴다.
    @Test
    void 시군구가_아닌_기관의_문자는_시군구를_모름으로_둔다() {
        // 준비: 발신 기관이 철도공사. 본문에는 장소(경부선 구간)가 있지만 시군구 이름은 없다
        AlertInput input = alert("경부선 열차 운행이 지연되고 있습니다. [한국철도공사]",
                LocalDateTime.of(2025, 8, 6, 10, 0), "교통", SANCHEONG);

        // 실행
        AlertFacts result = facts.analyze(input);

        // 검증: 기관은 있으나 시군구 기관이 아니고, 시군구는 비어 있다(= 모름). 수신 지역(산청)으로 대신 채우지 않는다
        assertThat(result.agencyKind()).isEqualTo(AgencyKind.NON_SIGUNGU);
        assertThat(result.sigunguId()).isEmpty();
    }

    // E: [인천서구청] 문자 하나가 개편 뒤 DB 에는 "검단구, 서해구" 두 곳으로 수신된 것으로 남아 있다.
    // 수신 지역 두 곳을 시군구 둘로 세면 안 되고, 발신 기관(옛 구청) 기준으로 폐지된 옛 서구 코드 하나로 본다.
    @Test
    void 인천_옛_구청은_새_구로_쪼개지_않고_옛_구청_하나의_시군구로_본다() {
        // 준비: 발신 기관은 옛 서구청, 수신 코드는 새 구 두 개(검단구·서해구)
        AlertInput input = alert("서구 일부 도로가 침수되어 통제합니다. [인천서구청]",
                LocalDateTime.of(2025, 8, 6, 10, 0), "호우", GEOMDAN, SEOHAE);

        // 실행
        AlertFacts result = facts.analyze(input);

        // 검증: 시군구 기관이고, 시군구는 검단구·서해구 어느 쪽도 아닌 폐지된 옛 서구 코드 하나
        assertThat(result.agencyKind()).isEqualTo(AgencyKind.SIGUNGU);
        assertThat(result.sigunguId()).contains(OLD_SEO);
    }

    // G: 시군구 경계에 걸친 다리(임천교)는 두 시군구가 각각 문자를 보낸다. 시설 이름이 같으면 같은 시설 "후보"로 올리고
    // (같은 시설·같은 비인지는 판단 몫), 시군구 관계는 이웃으로 낸다 — 이웃이라 A 규칙만으로는 다른 사건이 되는 쌍이다.
    @Test
    void 이웃_시군구가_같은_다리_이름을_쓰면_같은_시설_후보로_잡는다() {
        // 준비: 함양과 산청이 4분 간격으로 같은 다리(임천교) 통행제한 해제를 알린 실제 사례(기준 문서 G)
        LocalDateTime hamyangAt = LocalDateTime.of(2025, 8, 7, 14, 0);
        AlertInput hamyang = alert("임천교(유림면~화계리) 통행제한 해제 [함양군]", hamyangAt, "교통통제", HAMYANG);
        AlertInput sancheong = alert("임천교(금서면 화계리~유림면 화촌리) 통행제한 해제 [산청군]",
                hamyangAt.plusMinutes(4), "교통통제", SANCHEONG);

        // 실행: 두 문자를 견준다
        PairFacts result = facts.compare(hamyang, sancheong);

        // 검증: 시군구는 이웃(주입한 이웃 표 기준), 같이 나온 시설 이름은 임천교 하나
        // (유림면·화계리 같은 행정구역 이름은 시설 이름이 아니므로 후보에 들지 않는다)
        assertThat(result.relation()).isEqualTo(SigunguRelation.ADJACENT);
        assertThat(result.sharedFacilityNames()).containsExactly("임천교");
    }

    // D: 발신 기관은 본문 끝에만 있는 게 아니다. 평가 셋 문자 544건 중 27건은 "[세종시청] 오늘 …"처럼 맨 앞에 붙는다.
    // 끝만 보면 이 문자들은 기관이 없는 문자(NONE)가 되어 시군구를 모름으로 처리하게 되므로, 맨 앞도 발신 기관으로 읽는다.
    @Test
    void 발신_기관이_본문_맨_앞에_있어도_시군구_기관으로_읽는다() {
        // 준비: 대괄호가 맨 앞에만 있고 끝에는 없는 본문
        AlertInput input = alert("[세종시청] 오늘 9시 2분경 연동면 도로에 토사가 유출되어 통제 중입니다.",
                LocalDateTime.of(2025, 8, 6, 9, 5), "교통통제", SEJONG);

        // 실행
        AlertFacts result = facts.analyze(input);

        // 검증: 앞쪽 [ ] 안 문자열이 발신 기관이고, 부서가 없는 "세종시청"도 시군구 기관으로 세종 코드가 된다
        assertThat(result.senderAgency()).contains("세종시청");
        assertThat(result.agencyKind()).isEqualTo(AgencyKind.SIGUNGU);
        assertThat(result.sigunguId()).contains(SEJONG);
    }
}
