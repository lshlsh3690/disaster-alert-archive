package com.disaster.alert.alertapi.domain.event.evaluation;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 사실 계산기({@link AlertPlaceFacts})가 발신 기관 이름에서 시군구를 찾을 때 쓰는 "장소 사전"이다.
 * DB 를 읽지 않는 순수 값이며, 실제 DB 행에서 만드는 일은 {@link PlaceDirectoryBuilder} 가 맡는다.
 *
 * <p>이름 표를 "이름 → 식별자 하나"로 두면 안 된다: 북구·서구·동구처럼 여러 광역시에 같은 이름이 있어
 * 이름만으로는 시군구를 못 정하기 때문이다. 그래서 이름마다 시도를 같이 들고 있다.
 * <p>Red 단계 뼈대 — 구조만 있고, 채우는 로직은 Green 에서 쓴다.
 *
 * @param names        시군구 이름 항목들(같은 이름이 시도별로 여러 개 있을 수 있다)
 * @param sidoByAlias  시도 별칭(공백 없음, 예: "인천", "부산시") → 시도 2자리 코드
 * @param neighbors    시군구 식별자(5자리) → 이웃 시군구 식별자 집합
 */
public record PlaceDirectory(List<NameEntry> names,
                             Map<String, String> sidoByAlias,
                             Map<String, Set<String>> neighbors) {

    /**
     * 시군구 이름 항목 하나.
     *
     * @param name       공백 없는 시군구 이름(예: "서구", "천안시", "세종시")
     * @param sidoCode   소속 시도 2자리(예: 인천 "28")
     * @param sigunguId  시군구 식별자 5자리(예: 인천 옛 서구 "28260")
     */
    public record NameEntry(String name, String sidoCode, String sigunguId) {
    }

    /** 아무것도 모르는 빈 사전. */
    public static PlaceDirectory empty() {
        return new PlaceDirectory(List.of(), Map.of(), Map.of());
    }
}
