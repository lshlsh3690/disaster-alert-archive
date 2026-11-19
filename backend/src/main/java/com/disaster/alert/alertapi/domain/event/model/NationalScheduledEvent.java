package com.disaster.alert.alertapi.domain.event.model;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 전국 예정 행사(수능·민방위훈련) 판정 — 행사 문자를 사건·비사건 분류와 무관하게 "행사 하나 = 사건 하나"로 묶기 위한 키.
 *
 * <p>정해진 날 전국이 함께 치르는 행사는 사회적 관심이 큰데, 비사건 제외와 광역 {@code 기타} 미병합 때문에
 * 문자가 빠지거나 이벤트가 여러 개로 흩어졌다(2026-11-19 사용자 결정, CLUSTERING_CRITERIA.md 4절).
 * 판정 규칙이라 JPA·Spring 에 의존하지 않는 순수 클래스로 둔다(헌법 VI).
 */
// Red 단계 뼈대 — 구현은 Green 에서
public enum NationalScheduledEvent {
    /** 대학수학능력시험(수능). */
    SUNEUNG,
    /** 민방위훈련. */
    CIVIL_DEFENSE_DRILL;

    /**
     * 유형·본문이 행사 문자에 해당하면 그 행사 종류를, 아니면 빈 값을 돌려준다.
     */
    public static Optional<NationalScheduledEvent> match(String disasterType, String message) {
        return Optional.empty();
    }

    /**
     * 이 행사의 이벤트 제목 — 알림 시각의 연도로 정한다(수능은 다음 해 학년도, 민방위훈련은 그 해).
     */
    public String title(LocalDateTime createdAt) {
        return "";
    }
}
