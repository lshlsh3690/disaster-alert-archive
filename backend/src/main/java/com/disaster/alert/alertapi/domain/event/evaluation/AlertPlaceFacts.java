package com.disaster.alert.alertapi.domain.event.evaluation;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Red 단계 뼈대 — 구현은 Green 에서.
 *
 * <p>재난문자가 "어느 시군구의 문자인가"와 두 문자의 장소 관계를 기준 문서
 * (`specs/001-event-clustering-pipeline/CLUSTERING_CRITERIA.md` D·E·G·I) 대로 계산하는 순수 로직이다.
 * 같은/다른 사건 <b>판정은 내지 않고 사실만</b> 낸다 — 판정은 사람 라벨이나 LLM 판사의 몫이다.
 * 평가 도구의 재확인 후보 추출과 일일 채점 판사가 같이 쓴다.
 *
 * <p>DB 에 의존하지 않도록 시군구 이름 표와 이웃 표는 생성자로 받는다(실제 DB 로더는 별도 작업).
 */
public class AlertPlaceFacts {

    /**
     * 인천 옛 구청(E)은 DB 의 새 구(검단구·서해구 등)로 쪼개지 않고 이 접두사 + 옛 구청 이름
     * (예: {@code LEGACY:인천서구})을 시군구 식별자로 쓴다. DB 시군구 코드와 절대 겹치지 않게 하려는 표지다.
     */
    public static final String LEGACY_ID_PREFIX = "LEGACY:";

    /** 문자 하나의 입력. 수신 시군구 코드는 disaster_alert_region 의 시군구 코드 목록이다. */
    public record AlertInput(String body, LocalDateTime sentAt, String disasterType, List<String> receivedSigunguCodes) {
    }

    /** 발신 기관이 시군구 소속인지 (D·I). */
    public enum AgencyKind {
        /** 시청·군청·구청, 그 아래 부서·대책본부 */
        SIGUNGU,
        /** 철도·경찰청·도청·관리본부 등 시군구가 아닌 기관 */
        NON_SIGUNGU,
        /** 본문 끝에 발신 기관 표시가 없음 */
        NONE
    }

    /**
     * 문자 하나에서 뽑은 사실.
     *
     * @param senderAgency          본문 끝 `[...]` 안의 문자열(없으면 비어 있음)
     * @param agencyKind            발신 기관 종류
     * @param sigunguId             이 문자의 시군구 식별자. 시군구 기관이면 정해지고,
     *                              시군구가 아닌 기관이면 "모름"(비어 있음 — 문자 속 장소는 LLM 이 판단, I)
     * @param receivedSigunguCount  수신 시군구 개수(2개 이상이면 다지역 발송, D)
     */
    public record AlertFacts(Optional<String> senderAgency, AgencyKind agencyKind,
                             Optional<String> sigunguId, int receivedSigunguCount) {
    }

    /** 두 문자의 시군구 관계. 하나라도 시군구를 모르면 UNKNOWN. */
    public enum SigunguRelation {
        SAME, ADJACENT, OTHER, UNKNOWN
    }

    /**
     * 두 문자를 견준 사실.
     *
     * @param relation           두 문자의 시군구 관계
     * @param gap                발송 시각 차이(앞뒤 순서와 무관하게 0 이상)
     * @param sharedFacilityNames 두 본문에 같이 나오는 시설 이름 후보(같은 시설인지는 판단 몫, G)
     */
    public record PairFacts(SigunguRelation relation, Duration gap, List<String> sharedFacilityNames) {
    }

    /**
     * @param sigunguIdByName 시군구 이름(예: "산청군") → 시군구 식별자
     * @param adjacentIds     시군구 식별자 → 이웃 시군구 식별자 집합(region_adjacency)
     */
    public AlertPlaceFacts(Map<String, String> sigunguIdByName, Map<String, Set<String>> adjacentIds) {
    }

    /** 문자 하나의 사실을 계산한다. */
    public AlertFacts analyze(AlertInput alert) {
        return new AlertFacts(Optional.empty(), AgencyKind.NONE, Optional.empty(), 0);
    }

    /** 두 문자를 견준 사실을 계산한다. */
    public PairFacts compare(AlertInput a, AlertInput b) {
        return new PairFacts(SigunguRelation.UNKNOWN, Duration.ZERO, List.of());
    }
}
