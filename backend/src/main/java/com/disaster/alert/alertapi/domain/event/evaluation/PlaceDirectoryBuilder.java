package com.disaster.alert.alertapi.domain.event.evaluation;

import java.util.List;

/**
 * legal_district(시군구 행)과 region_adjacency(이웃 간선 행)로 {@link PlaceDirectory} 를 만드는 순수 변환기다.
 * 평가 도구가 DB 에서 두 표를 읽어 이 클래스에 넘기면, 사실 계산기가 쓸 사전이 나온다(DB 로더는 별도 작업).
 *
 * <p>Red 단계 뼈대 — 구조만 있고 빈 사전을 돌려준다. 이름 항목·시도 별칭·이웃 합집합 규칙은 Green 에서 쓴다.
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

    // Red 단계 뼈대 — 구현은 Green 에서
    public static PlaceDirectory build(List<DistrictRow> districts, List<AdjacencyRow> adjacency) {
        return PlaceDirectory.empty();
    }
}
