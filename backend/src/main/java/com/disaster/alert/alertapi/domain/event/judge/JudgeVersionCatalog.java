package com.disaster.alert.alertapi.domain.event.judge;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 판사 프롬프트 버전별 모델·272쌍 일치율·단가를 한 곳에 모은 목록(순수 클래스, JPA·Spring 없음).
 * 판사·리포트·관리자 대시보드가 같은 값을 읽게 해서, 모델을 바꿀 때 한 곳만 고치도록 한다.
 *
 * <p>// Red 단계 뼈대 — 구현은 Green 에서
 */
public final class JudgeVersionCatalog {

    private JudgeVersionCatalog() {
    }

    /**
     * 버전 하나의 기록. 일치율은 분자/분모 그대로 들고 있고(백분율로 바꾸지 않음),
     * 단가는 USD / 100만 토큰이다. measuredOn 은 그 일치율을 잰 날짜다.
     */
    public record Entry(String version, String model,
                        int sameNum, int sameDen, int diffNum, int diffDen,
                        double inputUsdPerMillion, double outputUsdPerMillion,
                        LocalDate measuredOn) {
    }

    /** 모든 버전, 최신 버전이 먼저. */
    public static List<Entry> all() {
        return List.of();
    }

    /** 버전 이름으로 찾는다. 목록에 없으면 비어 있다. */
    public static Optional<Entry> find(String version) {
        return Optional.empty();
    }
}
