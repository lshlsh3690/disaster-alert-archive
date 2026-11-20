package com.disaster.alert.alertapi.domain.event.model;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 전국 예정 행사(수능·민방위훈련) 판정 — 행사 문자를 사건·비사건 분류와 무관하게 "행사 하나 = 사건 하나"로 묶기 위한 키.
 *
 * <p>정해진 날 전국이 함께 치르는 행사는 사회적 관심이 큰데, 비사건 제외와 광역 {@code 기타} 미병합 때문에
 * 문자가 빠지거나 이벤트가 여러 개로 흩어졌다(2026-11-19 사용자 결정, CLUSTERING_CRITERIA.md 4절).
 * 판정 규칙이라 JPA·Spring 에 의존하지 않는 순수 클래스로 둔다(헌법 VI).
 */
public enum NationalScheduledEvent {
    /** 대학수학능력시험(수능). */
    SUNEUNG,
    /** 민방위훈련. */
    CIVIL_DEFENSE_DRILL;

    /**
     * 행사 문자가 오는 유형. 한파·산불 같은 사고 유형 문자는 본문에 "수능 당일"이 들어 있어도
     * (한파 안내가 수능 날씨를 언급) 원래 기준을 따라야 하므로 제외한다 — 훈련 경계경보만 {@code 민방공}.
     */
    private static final Set<String> EVENT_TYPES = Set.of("기타", "민방공");

    /**
     * 수능 본문 키워드 — "대학수학능력시험" 전체 이름, 또는 "수능" 바로 뒤(공백 허용)에 일·당일·시험·부정.
     * 그냥 "수능" 만 찾으면 지명 "수능리"(산불 문자)와 "연수능허대축제"가 걸린다(2026-11-19 운영 데이터).
     * 뒤 글자를 한정하는 것만으로 두 오탐이 모두 빠져 앞 글자 조건은 두지 않았다.
     * 운영 본문 "수능 시험 부정 방지 문자입니다", "수능부정행위 방지" 모두 이 꼴이다.
     */
    private static final Pattern SUNEUNG_PATTERN =
            Pattern.compile("대학수학능력시험|수능\\s*(?:일|당일|시험|부정)");

    /** 민방위훈련 본문 키워드 — 한글 표기와 영문 병기("Civil Defense Drill/Exercise", 대소문자 무시). */
    private static final Pattern DRILL_PATTERN =
            Pattern.compile("민방위\\s*훈련|Civil\\s+Defense\\s+(?:Drill|Exercise)", Pattern.CASE_INSENSITIVE);

    /**
     * 유형·본문이 행사 문자에 해당하면 그 행사 종류를, 아니면 빈 값을 돌려준다.
     * 빈 값이면 호출한 쪽은 원래 클러스터링 경로를 그대로 탄다.
     */
    public static Optional<NationalScheduledEvent> match(String disasterType, String message) {
        // 1) 유형이 기타·민방공이 아니면 본문을 보지 않는다 — 사고 유형 문자가 행사에 묻히는 것을 막는다.
        if (disasterType == null || message == null || !EVENT_TYPES.contains(disasterType)) {
            return Optional.empty();
        }
        // 2) 본문 키워드로 행사를 가른다. 두 행사가 한 본문에 같이 나오는 문자는 없어 수능을 먼저 본다.
        if (SUNEUNG_PATTERN.matcher(message).find()) {
            return Optional.of(SUNEUNG);
        }
        if (DRILL_PATTERN.matcher(message).find()) {
            return Optional.of(CIVIL_DEFENSE_DRILL);
        }
        return Optional.empty();
    }

    /**
     * 이 행사의 이벤트 제목 — 알림 시각의 연도로 정한다(수능은 다음 해 학년도, 민방위훈련은 그 해).
     * 제목이 곧 "같은 행사" 키라 해마다 다음 행사와 서로 병합되지 않는다.
     * 수능 문자가 11월 시험 직전·당일에 오므로 알림 연도+1 이 학년도다(2026-11 → 2027학년도).
     */
    public String title(LocalDateTime createdAt) {
        int year = createdAt.getYear();
        return switch (this) {
            case SUNEUNG -> (year + 1) + "학년도 수능";
            case CIVIL_DEFENSE_DRILL -> year + " 민방위훈련";
        };
    }
}
