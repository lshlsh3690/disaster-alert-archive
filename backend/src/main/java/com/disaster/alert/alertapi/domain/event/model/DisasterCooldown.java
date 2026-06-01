package com.disaster.alert.alertapi.domain.event.model;

import java.util.Map;
import java.util.Set;

/**
 * 재난 유형 → cooldown 시간(hour) 매핑.
 *
 * <p>cooldown = "한 사건이 마지막 알림 이후 얼마나 오래 '진행 중'으로 간주되는가".
 * 이벤트 생성 시 1회 산정되어 {@code disaster_events.cooldown_hours} 에 저장되고,
 * 조회 시 {@code now - last_alert_at < cooldown} 으로 active 를 파생 계산한다.
 *
 * <p>분류 근거: 재난 종류별 실제 지속 기간.
 * <ul>
 *   <li>168h(7일): 산불/지진 등 1주~수주 지속 가능</li>
 *   <li>72h(3일): 태풍/홍수 등 2~3일</li>
 *   <li>24h(1일): 화재/정전 등 시간~하루</li>
 * </ul>
 * 미분류/기타/null 은 {@link #DEFAULT_HOURS}(72h).
 */
public final class DisasterCooldown {

    public static final int LONG_HOURS = 168;
    public static final int MID_HOURS = 72;
    public static final int SHORT_HOURS = 24;
    public static final int DEFAULT_HOURS = 72;

    private static final Set<String> LONG_TYPES = Set.of(
            "산불", "지진", "지진해일", "폭염", "한파", "전염병", "가축질병", "가뭄"
    );

    private static final Set<String> MID_TYPES = Set.of(
            "태풍", "홍수", "호우", "대설", "산사태", "풍랑", "황사", "환경오염", "미세먼지", "에너지"
    );

    private static final Set<String> SHORT_TYPES = Set.of(
            "화재", "붕괴", "폭발", "강풍", "정전", "수도", "통신", "금융",
            "테러", "민방공", "교통사고", "교통통제", "교통", "건조", "안개"
    );

    private static final Map<String, Integer> TYPE_TO_HOURS = build();

    private DisasterCooldown() {
    }

    /**
     * 재난 유형 문자열 → cooldown 시간. 미등록/null 은 기본 72h.
     */
    public static int hoursFor(String disasterType) {
        if (disasterType == null) {
            return DEFAULT_HOURS;
        }
        return TYPE_TO_HOURS.getOrDefault(disasterType.trim(), DEFAULT_HOURS);
    }

    private static Map<String, Integer> build() {
        var map = new java.util.HashMap<String, Integer>();
        LONG_TYPES.forEach(t -> map.put(t, LONG_HOURS));
        MID_TYPES.forEach(t -> map.put(t, MID_HOURS));
        SHORT_TYPES.forEach(t -> map.put(t, SHORT_HOURS));
        return Map.copyOf(map);
    }
}
