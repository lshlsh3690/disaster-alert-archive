package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.AgencyKind;
import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.AlertFacts;
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

    // 같은 이름의 구가 여러 시도에 있는 상황을 만드는 코드들. 북구는 부산(26320)·대구(27230), 서구는 인천 옛 서구·부산(26140)에 있다.
    private static final String BUSAN_SEO = "26140";
    private static final String BUSAN_BUK = "26320";
    private static final String DAEGU_BUK = "27230";
    // 광주: 경기 광주시(41610)와, 통합 뒤 시도 12 로 옮긴 광주권 구(광산구 12330)가 이름이 겹친다.
    private static final String GYEONGGI_GWANGJU = "41610";
    private static final String GWANGSAN = "12330";
    // 수원: 시 코드(41110)가 이름 항목이고, 수신 코드는 구(영통구 41117)로 온다.
    private static final String SUWON = "41110";
    private static final String SUWON_YEONGTONG = "41117";

    // 시군구 이름 항목: (공백 없는 이름, 시도 2자리, 시군구 식별자). 이름은 시도 표기 없이 구·군 이름만 둔다 —
    // 시도는 항목의 두 번째 칸이 따로 들고 있다. 같은 이름이 시도별로 여러 항목으로 들어 있다.
    // 북구는 대구 항목, 서구는 부산 항목을 일부러 앞에 뒀다 — 이름이 맞는 첫 항목을 그냥 고르는 구현은 부산 문자를 대구로 읽어 걸러진다.
    private static final List<PlaceDirectory.NameEntry> NAME_ENTRIES = List.of(
            new PlaceDirectory.NameEntry("산청군", "48", SANCHEONG),
            new PlaceDirectory.NameEntry("함양군", "48", HAMYANG),
            new PlaceDirectory.NameEntry("북구", "27", DAEGU_BUK),
            new PlaceDirectory.NameEntry("북구", "26", BUSAN_BUK),
            // 서구도 인천 옛 서구(폐지 코드)와 부산 서구가 같이 있다 — "인천서구청"은 이름 앞의 시도(인천)로 옛 서구를 골라야 한다.
            new PlaceDirectory.NameEntry("서구", "26", BUSAN_SEO),
            new PlaceDirectory.NameEntry("서구", "28", OLD_SEO),
            new PlaceDirectory.NameEntry("검단구", "28", GEOMDAN),
            new PlaceDirectory.NameEntry("서해구", "28", SEOHAE),
            new PlaceDirectory.NameEntry("광주시", "41", GYEONGGI_GWANGJU),
            new PlaceDirectory.NameEntry("광산구", "12", GWANGSAN),
            new PlaceDirectory.NameEntry("수원시", "41", SUWON),
            new PlaceDirectory.NameEntry("세종시", "36", SEJONG));

    // 시도 별칭 → 시도 2자리. 기관 이름 앞에 시도 표기가 있는지 볼 때 쓴다.
    private static final Map<String, String> SIDO_BY_ALIAS = Map.of(
            "인천", "28",
            "부산", "26",
            "부산시", "26",
            "대구", "27",
            // "광주시"는 경기 광주시 이름이면서, 통합된 광주(시도 12)를 가리키는 시도 별칭이기도 하다
            "광주시", "12");

    // 이웃 표: 산청과 함양은 서로 이웃(region_adjacency 는 양방향 행으로 들어 있다고 보고 양쪽을 다 둔다).
    private static final Map<String, Set<String>> ADJACENT_IDS = Map.of(
            SANCHEONG, Set.of(HAMYANG),
            HAMYANG, Set.of(SANCHEONG));

    private final AlertPlaceFacts facts =
            new AlertPlaceFacts(new PlaceDirectory(NAME_ENTRIES, SIDO_BY_ALIAS, ADJACENT_IDS));

    private static AlertSnapshot alert(String body, LocalDateTime sentAt, String type, String... receivedCodes) {
        // 사실 계산기는 알림 id 를 쓰지 않는다
        return new AlertSnapshot(0L, sentAt, type, body, List.of(receivedCodes));
    }

    // D: 발신 기관이 시군구 소속이면 부서 이름이 붙어도 시군구 기관이고, 그 시군구의 문자로 본다.
    // 부서명("상하수도과")까지 통째로 시군구 이름 표에서 찾으려 하면 못 찾아 "모름"이 되는 사고를 막는 결정이다.
    @Test
    void 시군구_부서_이름이_붙은_기관도_시군구_기관이고_그_시군구의_문자다() {
        // 준비: 본문 끝 [ ] 안이 "산청군" + 부서명. 수신 지역은 산청 한 곳
        AlertSnapshot input = alert("신안면 일부 지역 단수 중입니다. 양해 바랍니다. [산청군상하수도과]",
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
        AlertSnapshot input = alert("경부선 열차 운행이 지연되고 있습니다. [한국철도공사]",
                LocalDateTime.of(2025, 8, 6, 10, 0), "교통", SANCHEONG);

        // 실행
        AlertFacts result = facts.analyze(input);

        // 검증: 기관은 있으나 시군구 기관이 아니고, 시군구는 비어 있다(= 모름). 수신 지역(산청)으로 대신 채우지 않는다
        assertThat(result.agencyKind()).isEqualTo(AgencyKind.NON_SIGUNGU);
        assertThat(result.sigunguId()).isEmpty();
    }

    // E: [인천서구청] 문자 하나가 개편 뒤 DB 에는 "검단구, 서해구" 두 곳으로 수신된 것으로 남아 있다.
    // 수신 지역 두 곳을 시군구 둘로 세면 안 되고, 발신 기관(옛 구청) 기준으로 폐지된 옛 서구 코드 하나로 본다.
    // 서구는 부산(26140)에도 있다 — 이름 앞의 "인천"으로 인천 옛 서구를 골라야 한다(픽스처에서 부산 항목을 먼저 둬서
    // 첫 항목을 고르는 Red 뼈대는 부산 서구 26140 을 돌려줘 실패한다).
    @Test
    void 인천_옛_구청은_새_구로_쪼개지_않고_옛_구청_하나의_시군구로_본다() {
        // 준비: 발신 기관은 옛 서구청, 수신 코드는 새 구 두 개(검단구·서해구)
        AlertSnapshot input = alert("서구 일부 도로가 침수되어 통제합니다. [인천서구청]",
                LocalDateTime.of(2025, 8, 6, 10, 0), "호우", GEOMDAN, SEOHAE);

        // 실행
        AlertFacts result = facts.analyze(input);

        // 검증: 시군구 기관이고, 시군구는 검단구·서해구 어느 쪽도 아닌 폐지된 옛 서구 코드 하나
        assertThat(result.agencyKind()).isEqualTo(AgencyKind.SIGUNGU);
        assertThat(result.sigunguId()).contains(OLD_SEO);
    }

    // 이름이 여러 시도에 겹치는 구("북구")는 이름만으로 못 정한다. 기관 이름에 시도 표기가 없으면 수신 지역 코드의 시도(앞 2자리)로 고른다.
    // 이름만 보고 고르면 부산에서 온 [북구] 문자가 대구 북구로 읽혀 두 문자의 시군구 관계가 틀어진다.
    // Red 단계에서는 시도를 무시하고 이름이 맞는 첫 항목(대구 북구 27230)을 돌려줘 부산 북구가 아니라서 실패한다.
    @Test
    void 시도_표기_없는_구_이름은_수신_지역의_시도로_고른다() {
        // 준비: 발신 기관이 "북구" 하나뿐이라 시도 단서가 없고, 수신 지역은 부산(26) 코드
        AlertSnapshot input = alert("북구 일부 지역 단수 중입니다. [북구]",
                LocalDateTime.of(2025, 8, 6, 10, 0), "수도", BUSAN_BUK);

        // 실행
        AlertFacts result = facts.analyze(input);

        // 검증: 대구 북구(27230)가 아니라 수신 지역 시도(부산)의 북구여야 한다
        assertThat(result.agencyKind()).isEqualTo(AgencyKind.SIGUNGU);
        assertThat(result.sigunguId()).contains(BUSAN_BUK);
    }

    // 광역 단위 기관([부산시])은 시군구 기관이 아니다. 앞부분에 "부산"이라는 시도 표기가 있어도 그 뒤에 구·군 이름이 없으면
    // 부산의 어느 구도 아니므로 시군구는 "모름"이어야 한다(수신 지역이나 이름이 같은 구로 대신 채우지 않는다).
    // 참고: 뼈대가 이름을 못 찾는 쪽으로 이미 동작해 Red 에서도 통과한다 — Green 의 시도 별칭 처리가 이 결과를 깨지 않는지 고정하는 용도다.
    @Test
    void 광역_단위_기관은_시군구_기관이_아니다() {
        // 준비: 발신 기관이 "부산시", 수신 지역은 부산 구 하나(서구)
        AlertSnapshot input = alert("부산 전역에 호우주의보가 발효되었습니다. [부산시]",
                LocalDateTime.of(2025, 8, 6, 10, 0), "호우", BUSAN_SEO);

        // 실행
        AlertFacts result = facts.analyze(input);

        // 검증: 기관은 있으나 시군구가 아닌 기관이고, 시군구는 모름
        assertThat(result.agencyKind()).isEqualTo(AgencyKind.NON_SIGUNGU);
        assertThat(result.sigunguId()).isEmpty();
    }

    // 이름이 하나뿐이어도("광주시" = 경기 광주시 41610) 수신 지역의 시도(12 광주)와 다르고 기관 이름이 그 시도의 이름("광주시" → 12)으로
    // 읽히면 시군구가 아니라 광역 단위(시도) 기관이다. 운영 [광주시] 76건 중 75건은 경기 광주시, 1건이 광주 수신 문자였다 —
    // 이 1건을 경기 광주시로 읽으면 광주 문자가 경기 시군구로 둔갑해 다른 사건과 엉뚱한 장소 관계가 계산된다.
    // 현재 구현은 이름 항목이 하나뿐이면 수신 시도와 무관하게 41610 으로 읽어 실패한다.
    @Test
    void 이름이_하나뿐이어도_수신_시도의_시도_이름이면_광역_기관으로_본다() {
        // 준비: 발신 기관 "광주시", 수신 지역은 광주권 구(광산구, 시도 12) — 경기(41)가 아니다
        AlertSnapshot input = alert("광산구 상가 화재 발생, 인근 주민은 창문을 닫아 주십시오. [광주시]",
                LocalDateTime.of(2025, 8, 6, 10, 0), "화재", GWANGSAN);

        // 실행
        AlertFacts result = facts.analyze(input);

        // 검증: 경기 광주시(41610)로 읽지 않고, 시군구가 아닌 기관 + 시군구 모름
        assertThat(result.agencyKind()).isEqualTo(AgencyKind.NON_SIGUNGU);
        assertThat(result.sigunguId()).isEmpty();
    }

    // 수원·용인·고양·창원은 "특례시"가 붙은 공식 이름으로도 문자를 보낸다. 법정동 이름은 "수원시"라 "수원특례시"로는 이름 표에 안 맞아
    // 시군구가 아닌 기관(NON_SIGUNGU)으로 잘못 분류된다. 운영 모의 계산에서 [수원특례시] 24건·[용인특례시] 6건, 모두 30건이 그랬다.
    // 기관 이름의 "특례시"를 "시"로 바꿔 찾으면 시군구 기관이어야 한다. 현재 구현은 이름이 안 맞아 NON_SIGUNGU 로 실패한다.
    @Test
    void 특례시_표기의_기관도_시_이름으로_찾아_시군구_기관으로_본다() {
        // 준비: 발신 기관 "수원특례시", 수신 지역은 수원의 구(영통구) — 이름 항목은 시 코드(41110)의 "수원시"
        AlertSnapshot input = alert("수원시 일부 지역 정전이 발생했습니다. [수원특례시]",
                LocalDateTime.of(2025, 8, 6, 10, 0), "정전", SUWON_YEONGTONG);

        // 실행
        AlertFacts result = facts.analyze(input);

        // 검증: 시군구 기관이고, 시군구는 수원시(시 코드 41110) — 수신 구 코드(41117)로 대신 채우지 않는다
        assertThat(result.agencyKind()).isEqualTo(AgencyKind.SIGUNGU);
        assertThat(result.sigunguId()).contains(SUWON);
    }

    // G: 시군구 경계에 걸친 다리(임천교)는 두 시군구가 각각 문자를 보낸다. 시설 이름이 같으면 같은 시설 "후보"로 올리고
    // (같은 시설·같은 비인지는 판단 몫), 시군구 관계는 이웃으로 낸다 — 이웃이라 A 규칙만으로는 다른 사건이 되는 쌍이다.
    @Test
    void 이웃_시군구가_같은_다리_이름을_쓰면_같은_시설_후보로_잡는다() {
        // 준비: 함양과 산청이 4분 간격으로 같은 다리(임천교) 통행제한 해제를 알린 실제 사례(기준 문서 G)
        LocalDateTime hamyangAt = LocalDateTime.of(2025, 8, 7, 14, 0);
        AlertSnapshot hamyang = alert("임천교(유림면~화계리) 통행제한 해제 [함양군]", hamyangAt, "교통통제", HAMYANG);
        AlertSnapshot sancheong = alert("임천교(금서면 화계리~유림면 화촌리) 통행제한 해제 [산청군]",
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
        AlertSnapshot input = alert("[세종시청] 오늘 9시 2분경 연동면 도로에 토사가 유출되어 통제 중입니다.",
                LocalDateTime.of(2025, 8, 6, 9, 5), "교통통제", SEJONG);

        // 실행
        AlertFacts result = facts.analyze(input);

        // 검증: 앞쪽 [ ] 안 문자열이 발신 기관이고, 부서가 없는 "세종시청"도 시군구 기관으로 세종 코드가 된다
        assertThat(result.senderAgency()).contains("세종시청");
        assertThat(result.agencyKind()).isEqualTo(AgencyKind.SIGUNGU);
        assertThat(result.sigunguId()).contains(SEJONG);
    }
}
