package com.disaster.alert.alertapi.domain.event.evaluation;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 재난문자가 "어느 시군구의 문자인가"와 두 문자의 장소 관계를 기준 문서
 * (`specs/001-event-clustering-pipeline/CLUSTERING_CRITERIA.md` D·E·G·I) 대로 계산하는 순수 로직이다.
 * 같은/다른 사건 <b>판정은 내지 않고 사실만</b> 낸다 — 판정은 사람 라벨이나 LLM 판사의 몫이다.
 * 평가 도구의 재확인 후보 추출과 일일 채점 판사가 같이 쓴다.
 *
 * <p>DB 에 의존하지 않도록 시군구 이름 표와 이웃 표는 생성자로 받는다(실제 DB 로더는 별도 작업).
 */
public class AlertPlaceFacts {

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

    // 본문 맨 끝(뒤 공백 허용)의 [발신기관]. 본문 중간의 대괄호([긴급] 같은 말머리)는 발신 기관이 아니므로 끝만 본다.
    private static final Pattern SENDER_AT_END = Pattern.compile("\\[([^\\[\\]]+)]\\s*$");

    // 시설 이름 후보: 한글·숫자 덩어리가 시설 접미사로 "끝나는" 경우만 잡는다. 끝 경계(?![가-힣0-9])가 없으면
    // "교통통제"에서 "교"를 잡는 식으로 일반 낱말이 시설로 오인된다. 앞에 최소 1글자(+)를 요구해 접미사만 홀로 잡히지 않게 한다.
    private static final Pattern FACILITY_TOKEN =
            Pattern.compile("[가-힣0-9]+(?:지하차도|터널|저수지|교|천|강)(?![가-힣0-9])");

    private final Map<String, String> sigunguIdByName;
    private final Map<String, Set<String>> adjacentIds;

    /**
     * @param sigunguIdByName 시군구 이름(예: "산청군") → 시군구 식별자. 폐지된 인천 옛 구(예: "인천서구" → 옛 서구 코드)도
     *                        들어온다 — E: 옛 구청 문자는 새 구(검단구·서해구)로 쪼개지 않고 폐지 코드 하나로 본다
     * @param adjacentIds     시군구 식별자 → 이웃 시군구 식별자 집합(region_adjacency)
     */
    public AlertPlaceFacts(Map<String, String> sigunguIdByName, Map<String, Set<String>> adjacentIds) {
        this.sigunguIdByName = sigunguIdByName;
        this.adjacentIds = adjacentIds;
    }

    /**
     * 문자 하나의 사실을 계산한다.
     *
     * <p>시군구는 수신 지역이 아니라 발신 기관 이름으로만 정한다. 다지역 발송(D)이나 개편 전후 코드 차이(E)에서
     * 수신 코드는 한 문자가 여러 시군구로 보이게 만들기 때문이다.
     */
    public AlertFacts analyze(AlertInput alert) {
        int receivedCount = (int) alert.receivedSigunguCodes().stream().distinct().count();

        // 1) 발신 기관을 본문 끝에서 뽑는다 — 없으면 시군구를 판단할 근거가 없으니 NONE
        Matcher sender = SENDER_AT_END.matcher(alert.body());
        if (!sender.find()) {
            return new AlertFacts(Optional.empty(), AgencyKind.NONE, Optional.empty(), receivedCount);
        }
        String agency = sender.group(1);

        // 2) 공백을 없앤 기관 이름의 앞부분이 시군구 이름과 가장 길게 맞는 것을 찾는다 —
        //    부서·대책본부가 붙어도("산청군상하수도과") 시군구 기관이고, 길게 맞는 쪽을 골라 짧은 이름에 잘못 걸리는 것을 막는다
        String compact = agency.replaceAll("\\s+", "");
        Optional<String> sigunguId = sigunguIdByName.entrySet().stream()
                .filter(e -> compact.startsWith(e.getKey()))
                .max((x, y) -> Integer.compare(x.getKey().length(), y.getKey().length()))
                .map(Map.Entry::getValue);

        // 3) 못 찾으면 시군구가 아닌 기관(I) — 수신 지역으로 대신 채우지 않고 "모름"으로 둔다
        AgencyKind kind = sigunguId.isPresent() ? AgencyKind.SIGUNGU : AgencyKind.NON_SIGUNGU;
        return new AlertFacts(Optional.of(agency), kind, sigunguId, receivedCount);
    }

    /**
     * 두 문자를 견준 사실을 계산한다. 각 문자의 시군구는 {@link #analyze} 와 같은 방식으로 정한다.
     */
    public PairFacts compare(AlertInput a, AlertInput b) {
        // 1) 시군구 관계 — 하나라도 모르면(시군구가 아닌 기관 등) 같다/다르다를 말하지 않는다
        Optional<String> idA = analyze(a).sigunguId();
        Optional<String> idB = analyze(b).sigunguId();
        SigunguRelation relation = SigunguRelation.UNKNOWN;
        if (idA.isPresent() && idB.isPresent()) {
            if (idA.get().equals(idB.get())) {
                relation = SigunguRelation.SAME;
            } else if (adjacentIds.getOrDefault(idA.get(), Set.of()).contains(idB.get())) {
                relation = SigunguRelation.ADJACENT;
            } else {
                relation = SigunguRelation.OTHER;
            }
        }

        // 2) 간격은 순서와 무관한 절댓값 — 어느 문자가 먼저인지는 호출자 사정이다
        Duration gap = Duration.between(a.sentAt(), b.sentAt()).abs();

        // 3) 같은 시설 후보 — a 본문의 등장 순서를 유지하며 b 본문에도 있는 것만 남긴다
        Set<String> inB = facilityTokens(b.body());
        List<String> shared = facilityTokens(a.body()).stream().filter(inB::contains).toList();
        return new PairFacts(relation, gap, shared);
    }

    // 본문에서 시설 이름 후보를 등장 순서대로(중복 제거) 뽑는다. 행정구역(…면·…리)은 접미사 목록에 없어 안 잡힌다.
    // 알려진 한계: "하천"·"학교" 같은 일반 명사도 접미사로 끝나면 잡히고, 조사가 붙은 "임천교는"은 못 잡는다.
    private static Set<String> facilityTokens(String body) {
        Set<String> tokens = new LinkedHashSet<>();
        Matcher m = FACILITY_TOKEN.matcher(body);
        while (m.find()) {
            tokens.add(m.group());
        }
        return tokens;
    }
}
