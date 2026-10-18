package com.disaster.alert.alertapi.domain.event.model;

import java.time.LocalDateTime;
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
 *   <li>72h(3일): 태풍/홍수/화재 등 2~3일 (화재는 대형 화재가 며칠 이어지는 경우를 반영)</li>
 *   <li>24h(1일): 정전/붕괴/폭발 등 시간~하루</li>
 * </ul>
 * 미분류/기타/null 은 {@link #DEFAULT_HOURS}(72h).
 */
public final class DisasterCooldown {

    public static final int LONG_HOURS = 168;   //일주일
    public static final int MID_HOURS = 72;
    public static final int SHORT_HOURS = 24;
    public static final int DEFAULT_HOURS = 72;

    private static final Set<String> LONG_TYPES = Set.of(
            "산불", "지진", "지진해일", "폭염", "한파", "전염병", "가축질병", "가뭄"
    );

    private static final Set<String> MID_TYPES = Set.of(
            "태풍", "홍수", "호우", "대설", "산사태", "풍랑", "황사", "환경오염", "미세먼지", "에너지", "화재"
    );

    private static final Set<String> SHORT_TYPES = Set.of(
            "붕괴", "폭발", "강풍", "정전", "수도", "통신", "금융",
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

    /**
     * LLM 폴백 병합 후보로 이 이벤트를 허용할지. 화재만 제한한다 — 별개 화재를 한 사건으로 합치는 오병합 방지.
     *
     * <p>기준을 첫 알림이 아니라 "마지막 알림"으로 잡아, 며칠 이어지는 대형 화재도 후속 알림이
     * 계속 오는 한 잘리지 않는다. 한도는 화재 cooldown({@code hoursFor("화재")})을 재사용하며
     * 정확히 그 시간 차이는 허용한다. 화재 외 유형과 null 입력은 항상 true.
     */
    public static boolean allowsLlmFallbackMerge(String alertType,
            LocalDateTime eventFirstAlertAt, LocalDateTime eventLastAlertAt,
            LocalDateTime alertCreatedAt) {
        if (alertType == null || !"화재".equals(alertType.trim())
                || eventFirstAlertAt == null || eventLastAlertAt == null || alertCreatedAt == null) {
            return true;
        }
        if (alertCreatedAt.isBefore(eventFirstAlertAt)) {
            return false;
        }
        return !eventLastAlertAt.isBefore(alertCreatedAt.minusHours(hoursFor("화재")));
    }

    private static Map<String, Integer> build() {
        var map = new java.util.HashMap<String, Integer>();
        LONG_TYPES.forEach(t -> map.put(t, LONG_HOURS));
        MID_TYPES.forEach(t -> map.put(t, MID_HOURS));
        SHORT_TYPES.forEach(t -> map.put(t, SHORT_HOURS));
        return Map.copyOf(map);
    }
}
