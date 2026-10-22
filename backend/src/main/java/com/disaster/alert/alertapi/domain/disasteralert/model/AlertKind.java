package com.disaster.alert.alertapi.domain.disasteralert.model;

import java.util.Locale;

/**
 * 재난문자의 성격. INCIDENT = 실제 발생한 사건(실종 수배 포함), ADVISORY = 특보·예방 안내 등 비사건.
 */
public enum AlertKind {
    INCIDENT,
    ADVISORY;

    /**
     * 모델 응답을 저장용 분류로 변환한다. 분류 불가면 null(미분류).
     * 앞뒤 공백·대소문자만 무시하고 한 단어가 정확히 일치할 때만 읽는다 — contains 로 읽으면
     * "INCIDENT 가 아니다" 같은 부정문에서 사건/비사건이 뒤집히므로 추측하지 않는다.
     * MISSING_PERSON 은 저장 값이 둘뿐이라 INCIDENT(실종 수배도 실제 사건)로 접는다.
     */
    public static AlertKind fromModelAnswer(String answer) {
        if (answer == null) {
            return null;
        }
        return switch (answer.trim().toUpperCase(Locale.ROOT)) {
            case "INCIDENT", "MISSING_PERSON" -> INCIDENT;
            case "ADVISORY" -> ADVISORY;
            default -> null;
        };
    }
}
