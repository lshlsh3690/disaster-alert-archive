package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.PlaceDirectory.NameEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * legal_district(시군구 행)과 region_adjacency(이웃 간선 행)로 {@link PlaceDirectory} 를 만드는 순수 변환기다.
 * 평가 도구가 DB 에서 두 표를 읽어 이 클래스에 넘기면, 사실 계산기가 쓸 사전이 나온다(DB 로더는 별도 작업).
 *
 * <p>DB 표를 그대로 쓰면 안 되는 곳을 여기서 메운다: (1) region_adjacency 에는 구가 있는 시의 시 코드와 인천 옛 구 코드의
 * 간선이 없고, (2) 기관 이름은 시도를 뗀 짧은 이름("서구")이 필요하며, (3) 세종은 통칭("세종시")으로 불린다.
 */
public class PlaceDirectoryBuilder {

    /**
     * legal_district 의 시군구 행 하나.
     *
     * @param code    법정동 코드 10자리(예: "2826000000")
     * @param name    시도·시군구 이름 전체(예: "인천광역시 서구 (폐지)")
     * @param active  is_active — 폐지된 행은 false
     */
    public record DistrictRow(String code, String name, boolean active) {
    }

    /**
     * region_adjacency 의 간선 행 하나(5자리 코드, 양방향이 각각 한 행).
     *
     * @param code      시군구 5자리 코드
     * @param neighbor  이웃 시군구 5자리 코드
     */
    public record AdjacencyRow(String code, String neighbor) {
    }

    // 폐지됐어도 이름 후보에 넣는 인천 옛 구(E) — [인천서구청] 같은 옛 구청 문자가 지금도 오기 때문이다. 그 밖의 폐지 행은 넣지 않는다.
    private static final Set<String> KEPT_INACTIVE_CODES = Set.of("28110", "28140", "28260");

    // 인천 옛 구 → 후속 새 구. 옛 구는 사라지고 새 구로 대체돼 V126 이 옛 구 간선도 지웠기 때문에, 이웃을 새 구들에서 빌려 온다.
    private static final Map<String, List<String>> SUCCESSORS = Map.of(
            "28260", List.of("28275", "28290"),
            "28110", List.of("28125", "28155"),
            "28140", List.of("28125"));

    // 시도 별칭 → 시도 2자리. 기관 이름 앞에 시도 표기가 붙은 경우("인천서구청", "부산시북구")를 떼어 내는 데 쓴다.
    // 광주는 2026-07 통합으로 전남광주통합특별시(12)가 됐다. "광주시"는 경기 광주시 이름이기도 해서, 이름 항목이 먼저 걸리되
    // 수신 지역이 12 일 때만 광주(광역) 기관으로 읽는다(AlertPlaceFacts.resolve) — 수신이 경기면 경기 광주시 그대로다.
    private static final Map<String, String> SIDO_BY_ALIAS = buildSidoAliases();

    /**
     * 장소 사전을 만든다.
     *
     * @param districts  legal_district 시군구·시도 행(읍면동 행이 섞여 있어도 무시된다)
     * @param adjacency  region_adjacency 간선 행
     */
    public static PlaceDirectory build(List<DistrictRow> districts, List<AdjacencyRow> adjacency) {
        // 1) 이름 항목 — 시군구 행만, 폐지는 인천 옛 구 3개만
        List<NameEntry> names = new ArrayList<>();
        Set<String> activeSigungu = new LinkedHashSet<>();
        for (DistrictRow row : districts) {
            if (!isSigunguRow(row.code())) {
                continue; // 시도 행(XX00000000)과 읍면동 행은 시군구 이름 항목이 아니다
            }
            String id = row.code().substring(0, 5);
            if (!row.active() && !KEPT_INACTIVE_CODES.contains(id)) {
                continue;
            }
            if (row.active()) {
                activeSigungu.add(id);
            }
            addNameEntries(names, row, id);
        }
        // 2) 이웃 — 간선 행 그대로, 그다음 시 코드·옛 구 코드를 보충한다
        return new PlaceDirectory(names, SIDO_BY_ALIAS, buildNeighbors(adjacency, activeSigungu));
    }

    // 시군구 행 = 10자리 중 뒤 5자리가 00000 이고, 앞 5자리의 3~5번째가 000 이 아닌 것(000 이면 시도 행).
    private static boolean isSigunguRow(String code) {
        return code.length() == 10 && code.endsWith("00000") && !code.startsWith("000", 2);
    }

    // 이름 = 전체 이름에서 "(폐지)" 표기와 시도 토큰을 뺀 나머지의 공백 제거. 시도 토큰이 곧 전부인 행(세종)은 통칭 "세종시"로 만든다.
    private static void addNameEntries(List<NameEntry> names, DistrictRow row, String id) {
        String[] tokens = row.name().replace("(폐지)", "").trim().split("\\s+");
        String sido = id.substring(0, 2);
        if (tokens.length == 1) {
            names.add(new NameEntry("세종시", sido, id)); // 시도=시군구인 행은 세종뿐이라 통칭을 고정한다
            return;
        }
        names.add(new NameEntry(String.join("", Arrays.copyOfRange(tokens, 1, tokens.length)), sido, id));
    }

    private static Map<String, Set<String>> buildNeighbors(List<AdjacencyRow> adjacency, Set<String> activeSigungu) {
        Map<String, Set<String>> neighbors = new HashMap<>();
        for (AdjacencyRow row : adjacency) {
            neighbors.computeIfAbsent(row.code(), k -> new LinkedHashSet<>()).add(row.neighbor());
        }
        // 1) 구가 있는 시(xxxx0 이고 같은 앞 4자리에 5번째 자리가 0 이 아닌 활성 행이 있는 것) → 하위 구 코드들
        Map<String, List<String>> wardsOfCity = new LinkedHashMap<>();
        Map<String, String> cityOfWard = new HashMap<>();
        for (String city : activeSigungu) {
            List<String> wards = activeSigungu.stream()
                    .filter(c -> c.startsWith(city.substring(0, 4)) && c.charAt(4) != '0').toList();
            if (city.endsWith("0") && !wards.isEmpty()) {
                wardsOfCity.put(city, wards);
                wards.forEach(w -> cityOfWard.put(w, city));
            }
        }
        // 2) 새로 만들 이웃을 간선 행만으로 모두 계산해 둔다 — 하나씩 넣어 가며 계산하면 처리 순서에 따라 결과가 달라진다.
        //    시는 하위 구 이웃의 합집합에서 하위 구 자신을 뺀다(구는 현행 코드로 남아 있어 시의 안쪽이다).
        //    인천 옛 구는 후속 새 구 이웃의 합집합에 새 구 자신도 넣는다(옛 코드가 사라지고 새 구로 대체돼 같은 지역이 나뉜 것).
        Map<String, Set<String>> derived = new LinkedHashMap<>();
        wardsOfCity.forEach((city, wards) -> derived.put(city, withCityCodes(unionOf(neighbors, wards, false), cityOfWard)));
        Map<String, Set<String>> oldDistricts = new LinkedHashMap<>();
        SUCCESSORS.forEach((old, successors) -> oldDistricts.put(old, withCityCodes(unionOf(neighbors, successors, true), cityOfWard)));
        derived.putAll(linkOldDistrictsSharingSuccessors(oldDistricts));
        // 3) 반영하고, 상대 쪽에도 되돌려 넣어 양방향으로 만든다 — compare 가 한쪽만 조회하므로 어느 쪽에서 봐도 이웃이어야 한다
        derived.forEach((code, found) -> {
            found.remove(code);
            if (!found.isEmpty()) { // 간선 자료가 없으면 빈 이웃을 만들지 않는다
                neighbors.put(code, found);
            }
        });
        derived.forEach((code, found) -> found.forEach(other -> neighbors.computeIfAbsent(other, k -> new LinkedHashSet<>()).add(code)));
        return neighbors;
    }

    // 후속 구를 공유하는 옛 구끼리(중구·동구 → 제물포구)는 같은 지역이 이어진 것이라 "그 밖"이 아니라 서로 이웃이다.
    // 판정은 합치기 전 원본 집합으로 하고 결과를 복사본에 넣어, 처리 순서에 따라 결과가 달라지지 않게 한다.
    private static Map<String, Set<String>> linkOldDistrictsSharingSuccessors(Map<String, Set<String>> oldDistricts) {
        Map<String, Set<String>> linked = new LinkedHashMap<>();
        oldDistricts.forEach((old, set) -> linked.put(old, new LinkedHashSet<>(set)));
        oldDistricts.forEach((x, setX) -> SUCCESSORS.forEach((y, successorsOfY) -> {
            if (!x.equals(y) && successorsOfY.stream().anyMatch(setX::contains)) {
                linked.get(x).add(y);
                linked.get(y).add(x);
            }
        }));
        return linked;
    }

    private static Set<String> unionOf(Map<String, Set<String>> neighbors, List<String> members, boolean includeMembers) {
        Set<String> union = new LinkedHashSet<>();
        members.forEach(m -> union.addAll(neighbors.getOrDefault(m, Set.of())));
        if (includeMembers) {
            union.addAll(members);
        } else {
            union.removeAll(members);
        }
        return union;
    }

    // 간선 행은 구 코드만 알아서 다른 시의 구(수원 영통구 등)가 이웃으로 들어온다. 그 시의 기관 문자는 시 코드로 읽히므로 시 코드도 같이 넣는다.
    private static Set<String> withCityCodes(Set<String> neighborIds, Map<String, String> cityOfWard) {
        Set<String> result = new LinkedHashSet<>(neighborIds);
        neighborIds.forEach(id -> {
            if (cityOfWard.containsKey(id)) {
                result.add(cityOfWard.get(id));
            }
        });
        return result;
    }

    private static Map<String, String> buildSidoAliases() {
        Map<String, String> m = new LinkedHashMap<>();
        alias(m, "11", "서울", "서울시", "서울특별시");
        alias(m, "26", "부산", "부산시", "부산광역시");
        alias(m, "27", "대구", "대구시", "대구광역시");
        alias(m, "28", "인천", "인천시", "인천광역시");
        alias(m, "12", "광주", "광주시", "광주광역시", "전남", "전라남도", "전남광주", "전남광주통합특별시");
        alias(m, "30", "대전", "대전시", "대전광역시");
        alias(m, "31", "울산", "울산시", "울산광역시");
        alias(m, "36", "세종", "세종특별자치시");
        alias(m, "41", "경기", "경기도");
        alias(m, "51", "강원", "강원도", "강원특별자치도");
        alias(m, "43", "충북", "충청북도");
        alias(m, "44", "충남", "충청남도");
        alias(m, "52", "전북", "전라북도", "전북특별자치도");
        alias(m, "47", "경북", "경상북도");
        alias(m, "48", "경남", "경상남도");
        alias(m, "50", "제주", "제주도", "제주특별자치도");
        return Map.copyOf(m);
    }

    private static void alias(Map<String, String> m, String sido, String... names) {
        for (String n : names) {
            m.put(n, sido);
        }
    }
}
