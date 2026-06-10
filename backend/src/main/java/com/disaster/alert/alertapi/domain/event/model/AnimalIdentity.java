package com.disaster.alert.alertapi.domain.event.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 동물 재난문자에서 종(species) 식별 — cross-region 병합의 하드게이트.
 *
 * <p>탈출·출몰 동물 문자는 "[지역] 인근 [동물] 출몰/탈출, 안전 유의, [지자체]" 로 거의 같은 템플릿이라
 * 본문 임베딩이 종과 무관하게 균일하게 높다(늑대 탈출 ≈ 멧돼지 출몰). 그래서 임베딩+LLM 만으로는
 * 종을 못 가르고 늑대 이벤트가 멧돼지 알림을 흡수하는 과병합이 난다. 실종 인물을 신원(이름+나이+키)으로
 * 결정적으로 가르듯, 동물은 <b>종</b>을 하드게이트로 써서 다른 종끼리는 구조적으로 안 묶이게 한다.
 *
 * <p>종 사전은 운영 데이터에서 실제 관측된 종으로 구성. 미식별(사전에 없음)이면 {@code null} →
 * cross-region 안 함(보수적). 추출과 Postgres {@code ~} 매칭이 같은 정규식을 공유하도록 POSIX
 * 안전 문자클래스({@code [0-9]}, {@code [ ]})만 쓴다(Java/Postgres 양쪽 호환).
 */
public final class AnimalIdentity {

    /**
     * canonical 종 → 별칭 포함 매칭 정규식. 순서가 우선순위(복합어·구체어 먼저).
     * 예: "멧돼지" 가 "돼지" 보다, "들개" 가 막연한 "개" 보다 앞.
     */
    private static final Map<String, String> SPECIES_REGEX = new LinkedHashMap<>();

    static {
        // 운영 데이터에서 실제 관측된 종만 — 별칭은 OR 로. 사전에 없는 종은 미식별(null)로 두어
        // cross-region 안 함(보수적). 새 종(동물원 탈출 등) 관측 시 여기에 한 줄 추가.
        // 막연한 1글자 토큰(사자→봉사자, 소→소방 등)은 오탐 위험이 있어 한정·구체화한다.
        SPECIES_REGEX.put("멧돼지", "멧돼지");
        SPECIES_REGEX.put("들개", "들개|대형견|유기견");
        SPECIES_REGEX.put("늑대", "늑대");
        SPECIES_REGEX.put("사슴", "사슴|고라니");
        SPECIES_REGEX.put("곰", "곰");
        SPECIES_REGEX.put("소", "소[ ]?[0-9]+[ ]?마리|소가|한우|젖소|황소");
        SPECIES_REGEX.put("뱀", "뱀");
    }

    private static final Map<String, Pattern> PATTERNS = new LinkedHashMap<>();

    static {
        SPECIES_REGEX.forEach((species, regex) -> PATTERNS.put(species, Pattern.compile(regex)));
    }

    /**
     * 토착종 — 지역에 귀속돼 국지적으로만 움직이는 야생동물(멧돼지·들개·뱀). 시군구 단위로 정밀
     * 태깅되며, cross-region 은 <b>시군구 인접</b>만 허용(옆 동네 이동은 살리되, 인접 시도를 전이적으로
     * 타고 전국이 한 사건으로 묶이는 과병합 차단). 그 외 종(늑대·사슴·곰·소)은 농장·동물원 탈출처럼
     * 멀리 이동하고 시도 레벨로 태깅되는 경우가 많아 시도 인접을 쓴다.
     */
    private static final Set<String> ENDEMIC = Set.of("멧돼지", "들개", "뱀");

    private AnimalIdentity() {
    }

    /**
     * 본문에서 종 식별 — 사전 순서대로 첫 매칭 canonical 종 반환. 없으면 null.
     */
    public static String species(String message) {
        if (message == null) {
            return null;
        }
        for (Map.Entry<String, Pattern> e : PATTERNS.entrySet()) {
            if (e.getValue().matcher(message).find()) {
                return e.getKey();
            }
        }
        return null;
    }

    /**
     * canonical 종의 매칭 정규식(별칭 포함) — Postgres {@code ~} 후보 게이트용.
     * 사전에 없는 종이면 null.
     */
    public static String speciesRegex(String species) {
        return species == null ? null : SPECIES_REGEX.get(species);
    }

    /**
     * 토착종 여부 — true 면 cross-region 인접 게이트를 <b>시군구</b> 단위로(국지 이동), false 면
     * <b>시도</b> 단위로(원거리 이동) 적용한다. 사전에 없는 종(null)도 false.
     */
    public static boolean isEndemic(String species) {
        return species != null && ENDEMIC.contains(species);
    }
}
