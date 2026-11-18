package com.disaster.alert.alertapi.domain.event.evaluation;

import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 재난문자가 "어느 시군구의 문자인가"와 두 문자의 장소 관계를 기준 문서
 * (`specs/001-event-clustering-pipeline/CLUSTERING_CRITERIA.md` D·E·G·I) 대로 계산하는 순수 로직이다.
 * 같은/다른 사건 <b>판정은 내지 않고 사실만</b> 낸다 — 판정은 사람 라벨이나 LLM 판사의 몫이다.
 * 평가 도구의 재확인 후보 추출과 일일 채점 판사가 같이 쓴다.
 *
 * <p>DB 에 의존하지 않도록 시군구 이름 표와 이웃 표는 생성자로 받는다(실제 DB 로더는 별도 작업).
 */
public class AlertPlaceFacts {

    /** 발신 기관이 시군구 소속인지 (D·I). */
    public enum AgencyKind {
        /** 시청·군청·구청, 그 아래 부서·대책본부 */
        SIGUNGU,
        /** 철도·경찰청·도청·관리본부 등 시군구가 아닌 기관 */
        NON_SIGUNGU,
        /** 본문 끝·맨 앞 어디에도 발신 기관 표시가 없음 */
        NONE
    }

    /**
     * 문자 하나에서 뽑은 사실.
     *
     * @param senderAgency          본문 끝(없으면 맨 앞) `[...]` 안의 문자열(없으면 비어 있음)
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

    // 발신 기관 표시는 본문 맨 끝(뒤 공백 허용)이 기본 형태다(평가 셋 544건 중 517건). 본문 중간의 대괄호는 기관이 아니므로 보지 않는다.
    private static final Pattern SENDER_AT_END = Pattern.compile("\\[([^\\[\\]]+)]\\s*$");

    // 끝에 없을 때만 보는 맨 앞 형태("[세종시청] 오늘 …", "[서울경찰청]용산구에서 …" — 27건). 괄호 뒤 공백은 있어도 없어도 된다.
    // 끝을 먼저 보는 이유: 끝 표시가 있던 기존 문자의 결과를 바꾸지 않기 위해서다(둘 다 있는 문자는 평가 셋에 없다).
    private static final Pattern SENDER_AT_START = Pattern.compile("^\\s*\\[([^\\[\\]]+)]");

    // 시설 이름 후보: 한글·숫자 덩어리가 시설 접미사로 "끝나는" 경우만 잡는다. 끝 경계(?![가-힣0-9])가 없으면
    // "교통통제"에서 "교"를 잡는 식으로 일반 낱말이 시설로 오인된다. 앞에 최소 1글자(+)를 요구해 접미사만 홀로 잡히지 않게 한다.
    private static final Pattern FACILITY_TOKEN =
            Pattern.compile("[가-힣0-9]+(?:지하차도|터널|저수지|교|천|강)(?![가-힣0-9])");

    private final PlaceDirectory directory;
    private final Map<String, Set<String>> adjacentIds;

    /**
     * @param directory 시군구 이름 항목(이름·시도·식별자)·시도 별칭·이웃 표를 담은 장소 사전. 북구·서구처럼 시도마다 겹치는
     *                  이름은 시도로 가려야 하므로 이름을 식별자 하나에 대응시키지 않는다. 폐지된 인천 옛 구도 이름 후보에 든다 —
     *                  E: 옛 구청 문자는 새 구(검단구·서해구)로 쪼개지 않고 폐지 코드 하나로 본다
     */
    public AlertPlaceFacts(PlaceDirectory directory) {
        this.directory = directory;
        this.adjacentIds = directory.neighbors();
    }

    /**
     * 문자 하나의 사실을 계산한다.
     *
     * <p>시군구는 수신 지역이 아니라 발신 기관 이름으로만 정한다. 다지역 발송(D)이나 개편 전후 코드 차이(E)에서
     * 수신 코드는 한 문자가 여러 시군구로 보이게 만들기 때문이다.
     */
    public AlertFacts analyze(AlertSnapshot alert) {
        int receivedCount = (int) alert.sigunguCodes().stream().distinct().count();

        // 1) 발신 기관을 본문 끝, 없으면 맨 앞에서 뽑는다 — 둘 다 없으면 시군구를 판단할 근거가 없으니 NONE
        Optional<String> sender = findSender(alert.message());
        if (sender.isEmpty()) {
            return new AlertFacts(Optional.empty(), AgencyKind.NONE, Optional.empty(), receivedCount);
        }
        String agency = sender.get();

        // 2) 공백을 없앤 기관 이름으로 시군구를 찾는다 — 부서·대책본부가 붙어도("산청군상하수도과") 시군구 기관이다
        // "특례시"는 "시"로 바꿔 찾는다 — 법정동 이름은 "수원시"라 공식 표기 "수원특례시"가 안 맞아 시군구 기관이 아닌 것으로
        // 잘못 분류됐다(운영 [수원특례시] 24건·[용인특례시] 6건). 발신 기관 원문(senderAgency)은 그대로 둔다.
        String compact = agency.replaceAll("\\s+", "").replace("특례시", "시");
        Set<String> receivedSidos = alert.sigunguCodes().stream()
                .map(code -> code.substring(0, 2)).collect(Collectors.toSet());
        Lookup found = resolve(compact, receivedSidos);

        // 3) 이름이 끝까지 안 맞으면 시군구가 아닌 기관(I)이고, 맞았는데 시도로도 못 좁히면 시군구 기관이지만 시군구는 "모름"
        //    어느 쪽이든 수신 지역으로 대신 채우지 않는다
        AgencyKind kind = found.nameMatched() ? AgencyKind.SIGUNGU : AgencyKind.NON_SIGUNGU;
        return new AlertFacts(Optional.of(agency), kind, found.sigunguId(), receivedCount);
    }

    // 이름 찾기 전략. 먼저 기관 이름 전체로 찾고, 아래 두 경우에는 앞의 시도 표기를 떼고 다시 찾는다.
    //  (가) 이름이 하나도 안 맞을 때("인천서구청" → "인천" + "서구청") — 이때는 그 시도로만 가린다.
    //  (나) 이름이 하나로 정해졌지만 그 시도가 수신 시도에 없고, 앞의 시도 표기는 수신 시도일 때 — 광역 기관이 구·시 이름과 겹친 경우다.
    //       운영 [광주시] 76건 중 광주 수신 1건이 경기 광주시(41610)로 읽히면 안 되기 때문이다.
    //  수신 시도가 비어 있거나 별칭이 수신 시도가 아니면 (나)는 걸리지 않아 후보 그대로 쓴다(산청군이 다른 시도로 보낸 문자 등).
    private Lookup resolve(String compact, Set<String> receivedSidos) {
        Lookup found = lookup(compact, receivedSidos);
        Optional<String> alias = directory.sidoByAlias().keySet().stream()
                .filter(compact::startsWith).max(Comparator.comparingInt(String::length));
        Optional<String> aliasSido = alias.map(directory.sidoByAlias()::get);
        // 식별자 앞 2자리가 곧 시도 코드다(5자리 시군구 코드의 구조)
        boolean ownSidoNotReceived = found.sigunguId().isPresent()
                && !receivedSidos.contains(found.sigunguId().get().substring(0, 2));
        boolean aliasSidoReceived = aliasSido.isPresent() && receivedSidos.contains(aliasSido.get());
        if (found.nameMatched() && !(ownSidoNotReceived && aliasSidoReceived)) {
            return found;
        }
        // 별칭을 뗀 나머지가 비면("부산시", "광주시") 이름이 없어 NOT_MATCHED 다(광역 단위 기관)
        return alias.map(a -> lookup(compact.substring(a.length()), Set.of(aliasSido.get())))
                .orElse(Lookup.NOT_MATCHED);
    }

    /** 이름 찾기 결과. nameMatched 는 이름이 하나라도 맞았는지, sigunguId 는 시도로 하나로 좁혀졌을 때만 있다. */
    private record Lookup(boolean nameMatched, Optional<String> sigunguId) {
        static final Lookup NOT_MATCHED = new Lookup(false, Optional.empty());
    }

    // 이름의 앞부분이 가장 길게 맞는 항목들을 모은 뒤, 같은 이름이 여러 시도에 있으면 allowedSidos 로 가린다.
    // 길게 맞는 쪽을 먼저 고르는 이유: 짧은 이름에 잘못 걸리는 것을 막기 위해서다(예: "천안시동남구"가 "천안시"에 걸리지 않게).
    private Lookup lookup(String name, Set<String> allowedSidos) {
        int longest = directory.names().stream().filter(e -> name.startsWith(e.name()))
                .mapToInt(e -> e.name().length()).max().orElse(0);
        if (longest == 0) {
            return Lookup.NOT_MATCHED;
        }
        List<PlaceDirectory.NameEntry> candidates = directory.names().stream()
                .filter(e -> name.startsWith(e.name()) && e.name().length() == longest).toList();
        if (candidates.size() > 1) {
            candidates = candidates.stream().filter(e -> allowedSidos.contains(e.sidoCode())).toList();
        }
        // 후보가 하나뿐이면 수신 시도와 달라도 그대로 쓴다(산청군처럼 이름이 유일한 경우). 둘 이상 남으면 정하지 못한다.
        Optional<String> id = candidates.size() == 1 ? Optional.of(candidates.get(0).sigunguId()) : Optional.empty();
        return new Lookup(true, id);
    }

    /**
     * 두 문자를 견준 사실을 계산한다. 각 문자의 시군구는 {@link #analyze} 와 같은 방식으로 정한다.
     */
    public PairFacts compare(AlertSnapshot a, AlertSnapshot b) {
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
        Set<String> inB = facilityTokens(b.message());
        List<String> shared = facilityTokens(a.message()).stream().filter(inB::contains).toList();
        return new PairFacts(relation, gap, shared);
    }

    // 발신 기관 문자열: 끝 대괄호 우선, 없으면 맨 앞 대괄호. 둘 다 없으면 비어 있다.
    private static Optional<String> findSender(String body) {
        Matcher atEnd = SENDER_AT_END.matcher(body);
        if (atEnd.find()) {
            return Optional.of(atEnd.group(1));
        }
        Matcher atStart = SENDER_AT_START.matcher(body);
        return atStart.find() ? Optional.of(atStart.group(1)) : Optional.empty();
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
