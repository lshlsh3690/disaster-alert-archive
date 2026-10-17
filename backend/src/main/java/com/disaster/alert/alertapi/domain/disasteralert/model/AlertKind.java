package com.disaster.alert.alertapi.domain.disasteralert.model;

/**
 * 재난문자의 성격. INCIDENT = 실제 발생한 사건(실종 수배 포함), ADVISORY = 특보·예방 안내 등 비사건.
 */
// Red 단계 뼈대 — 구현은 Green 에서
public enum AlertKind {
    INCIDENT,
    ADVISORY;

    /** 모델 응답 문자열을 저장용 분류로 변환한다. 분류 불가면 null. */
    public static AlertKind fromModelAnswer(String answer) {
        return null;
    }
}
