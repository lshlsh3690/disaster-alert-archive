package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.PlaceDirectory.NameEntry;
import com.disaster.alert.alertapi.domain.event.evaluation.PlaceDirectoryBuilder.AdjacencyRow;
import com.disaster.alert.alertapi.domain.event.evaluation.PlaceDirectoryBuilder.DistrictRow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * legal_district·region_adjacency 행으로 장소 사전을 만드는 규칙 테스트. DB 없이 행을 직접 넣는 순수 단위 테스트다.
 * Red 단계 뼈대는 빈 사전을 돌려주므로 아래 세 테스트 모두 "찾는 항목이 없어서" 단언에서 실패한다.
 */
class PlaceDirectoryBuilderTest {

    // region_adjacency 는 양방향이 각각 한 행이다 — 한 쌍을 두 행으로 만들어 주는 도우미.
    private static List<AdjacencyRow> bothWays(String a, String b) {
        return List.of(new AdjacencyRow(a, b), new AdjacencyRow(b, a));
    }

    // 구가 있는 시(천안시)는 시 코드(44130)로 보는데, region_adjacency 에는 구 코드(44131·44133)만 있다.
    // 시 코드의 이웃을 비워 두면 천안시 기관 문자가 항상 "이웃 아님(OTHER)"으로 계산되므로, 하위 구들의 이웃을 합쳐서 만든다.
    @Test
    void 구가_있는_시의_이웃은_하위_구들_이웃의_합집합이다() {
        // 준비: 천안시(시 행)와 동남구·서북구(구 행), 이웃 후보 아산(44200)·공주(44210)
        List<DistrictRow> districts = List.of(
                new DistrictRow("4413000000", "충청남도 천안시", true),
                new DistrictRow("4413100000", "충청남도 천안시 동남구", true),
                new DistrictRow("4413300000", "충청남도 천안시 서북구", true),
                new DistrictRow("4420000000", "충청남도 아산시", true),
                new DistrictRow("4421000000", "충청남도 공주시", true));
        // 동남구 이웃 = 아산, 서북구 이웃 = 아산·공주. 두 구도 서로 이웃(같은 시 안의 이웃)이다
        List<AdjacencyRow> adjacency = new ArrayList<>();
        adjacency.addAll(bothWays("44131", "44200"));
        adjacency.addAll(bothWays("44133", "44200"));
        adjacency.addAll(bothWays("44133", "44210"));
        adjacency.addAll(bothWays("44131", "44133"));

        // 실행
        PlaceDirectory directory = PlaceDirectoryBuilder.build(districts, adjacency);

        // 검증: 천안시(44130)의 이웃은 아산·공주 둘뿐 — 합집합이며, 하위 구(44131·44133) 자신은 이웃으로 들어가지 않는다
        assertThat(directory.neighbors().getOrDefault("44130", Set.of()))
                .containsExactlyInAnyOrder("44200", "44210");
    }

    // E: 인천 옛 서구(28260)는 폐지(is_active=false)라도 [인천서구청] 문자를 위해 이름 후보에 넣는다. 그런데 V126 이
    // 옛 구 간선을 지웠기 때문에 이웃은 옛 구를 이어받은 새 구(서해구 28275·검단구 28290)들의 이웃을 합쳐서 만든다.
    @Test
    void 인천_옛_서구는_폐지라도_이름_후보에_들고_이웃은_새_구들의_이웃과_새_구_자신이다() {
        // 준비: 폐지된 옛 서구 행("(폐지)" 표기가 이름에 붙어 있다)과 후속 새 구 두 개
        List<DistrictRow> districts = List.of(
                new DistrictRow("2826000000", "인천광역시 서구 (폐지)", false),
                new DistrictRow("2827500000", "인천광역시 서해구", true),
                new DistrictRow("2829000000", "인천광역시 검단구", true));
        // 새 구들의 이웃: 서해구 → 계양(28245)·검단구, 검단구 → 계양(28245)·김포(41570)·서해구. 옛 서구 자신의 간선은 없다
        List<AdjacencyRow> adjacency = new ArrayList<>();
        adjacency.addAll(bothWays("28275", "28245"));
        adjacency.addAll(bothWays("28290", "28245"));
        adjacency.addAll(bothWays("28290", "41570"));
        adjacency.addAll(bothWays("28275", "28290"));

        // 실행
        PlaceDirectory directory = PlaceDirectoryBuilder.build(districts, adjacency);

        // 검증 1: "서구"(시도 28)가 옛 서구 코드로 이름 후보에 있다 — "(폐지)" 표기와 시도 이름은 이름에 남지 않는다
        assertThat(directory.names()).contains(new NameEntry("서구", "28", "28260"));
        // 검증 2: 옛 서구의 이웃 = 서해구 이웃 ∪ 검단구 이웃 ∪ 서해구·검단구 자신. 천안시(하위 구 자신 제외)와 달리
        //        옛 코드는 사라지고 새 구로 대체돼 같은 지역이 둘로 나뉜 것이므로, 새 구 자신이 "그 밖"이 아니라 이웃이다
        assertThat(directory.neighbors().getOrDefault("28260", Set.of()))
                .containsExactlyInAnyOrder("28245", "41570", "28275", "28290");
    }

    // 세종은 시군구가 따로 없는 특별자치시라 시 하나가 시군구(36110)다. 법정동 이름은 "세종특별자치시"지만
    // 발신 기관은 [세종시청]처럼 통칭으로 쓰므로 "세종시"로도 찾을 수 있어야 한다(광역 기관 [부산시]·[제주도]와 달리 예외).
    @Test
    void 세종특별자치시는_세종시_통칭으로_찾을_수_있는_시군구다() {
        // 준비: 세종 행 하나
        List<DistrictRow> districts = List.of(
                new DistrictRow("3611000000", "세종특별자치시", true));

        // 실행
        PlaceDirectory directory = PlaceDirectoryBuilder.build(districts, List.of());

        // 검증: 이름 "세종시", 시도 36, 식별자 36110 항목이 있다
        assertThat(directory.names()).contains(new NameEntry("세종시", "36", "36110"));
    }

    // 인천 옛 중구(28110)·옛 동구(28140)는 둘 다 제물포구(28125)로 이어진 같은 지역이다. 이웃 표에서 서로를 "그 밖"으로 두면
    // 옛 중구청과 옛 동구청 문자가 OTHER 로 계산되므로, 후속 구를 공유하는 옛 구끼리는 서로 이웃으로 본다.
    @Test
    void 같은_후속_구로_이어진_인천_옛_구끼리는_서로_이웃이다() {
        // 준비: 폐지된 옛 중구·옛 동구, 후속 제물포구(28125)·영종구(28155) — 중구는 둘 다, 동구는 제물포구로만 이어진다
        List<DistrictRow> districts = List.of(
                new DistrictRow("2811000000", "인천광역시 중구 (폐지)", false),
                new DistrictRow("2814000000", "인천광역시 동구 (폐지)", false),
                new DistrictRow("2812500000", "인천광역시 제물포구", true),
                new DistrictRow("2815500000", "인천광역시 영종구", true));
        List<AdjacencyRow> adjacency = new ArrayList<>();
        adjacency.addAll(bothWays("28125", "28155"));
        adjacency.addAll(bothWays("28125", "28177"));

        // 실행
        PlaceDirectory directory = PlaceDirectoryBuilder.build(districts, adjacency);

        // 검증: 옛 중구의 이웃에 옛 동구가, 옛 동구의 이웃에 옛 중구가 있다(양방향)
        assertThat(directory.neighbors().getOrDefault("28110", Set.of())).contains("28140");
        assertThat(directory.neighbors().getOrDefault("28140", Set.of())).contains("28110");
    }
}
