package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.AgencyKind;
import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.AlertFacts;
import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.PairFacts;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 기준 문서(`specs/001-event-clustering-pipeline/CLUSTERING_CRITERIA.md`)에 새로 생긴 규칙 D·E·G·I 의 조건에 걸리는
 * 알림 쌍을 골라, 사람이 라벨을 다시 확인할 "재확인 후보"로 내놓는 순수 로직이다.
 * 판정(같은/다른 사건)은 내지 않고 조건에 걸렸는지만 본다. 조건 판정의 재료는 {@link AlertPlaceFacts} 의 결과다.
 */
public class RecheckCandidateFinder {

    /** 재확인 규칙. 기준 문서의 항목 이름과 같다. */
    public enum Rule {
        /** 한쪽이라도 수신 시군구가 2개 이상(다지역 발송) */
        D,
        /** 한쪽이라도 발신 기관이 인천 옛 구(중구·동구·서구, 2026-07 개편 전 코드) */
        E,
        /** 두 문자에 같은 시설 이름 후보가 있음 */
        G,
        /** 한쪽이라도 발신 기관이 시군구가 아님 */
        I
    }

    /**
     * 판정 대상 쌍 하나.
     *
     * @param alertIdA 쌍의 한쪽 알림 id (결과에서 쌍을 가리키는 키)
     * @param alertIdB 쌍의 다른 쪽 알림 id
     * @param a        알림 A 의 사실
     * @param b        알림 B 의 사실
     * @param pair     두 알림을 견준 사실
     */
    public record PairInput(long alertIdA, long alertIdB, AlertFacts a, AlertFacts b, PairFacts pair) {
    }

    // E: 2026-07 개편으로 폐지된 인천 옛 중구(28110)·동구(28140)·서구(28260) 시군구 식별자.
    // 옛 구청 문자는 새 구(영종구·제물포구·검단구·서해구)로 쪼개지 않고 폐지 코드 하나로 보므로, 예전 라벨 기준과 달라질 수 있다.
    private static final Set<String> OLD_INCHEON_SIGUNGU_IDS = Set.of("28110", "28140", "28260");

    /**
     * 요청한 규칙마다 조건에 걸린 쌍을 모아 돌려준다. 한 쌍이 여러 규칙에 걸리면 각 규칙 목록에 모두 들어간다.
     * 요청한 규칙은 걸린 쌍이 없어도 빈 목록으로 키가 남고, 요청하지 않은 규칙은 판정하지도 키로 두지도 않는다.
     * 목록 안의 쌍 순서는 입력 순서를 따른다 — 사람이 보는 재확인 목록이 실행마다 흔들리지 않게 하기 위해서다.
     */
    public Map<Rule, List<PairInput>> find(Set<Rule> rules, List<PairInput> pairs) {
        Map<Rule, List<PairInput>> result = new EnumMap<>(Rule.class);
        for (Rule rule : rules) {
            // 1) 요청한 규칙마다 입력 순서를 유지한 채 조건에 걸린 쌍만 남긴다
            result.put(rule, pairs.stream().filter(pair -> matches(rule, pair)).toList());
        }
        return result;
    }

    // 규칙별 조건. D·E·I 는 "한쪽이라도" 걸리면 쌍이 걸린다 — 어느 쪽 알림이 문제의 알림인지는 사람이 본다.
    private static boolean matches(Rule rule, PairInput pair) {
        return switch (rule) {
            // D: 다지역 발송 — 수신 시군구가 2개 이상
            case D -> pair.a().receivedSigunguCount() >= 2 || pair.b().receivedSigunguCount() >= 2;
            // E: 발신 기관이 인천 옛 구 — 시군구 식별자가 비어 있으면(모름) 걸리지 않는다
            case E -> isOldIncheon(pair.a()) || isOldIncheon(pair.b());
            // G: 두 본문에 같은 시설 이름 후보가 있음 — 같은 시설인지는 사람이 판단한다
            case G -> !pair.pair().sharedFacilityNames().isEmpty();
            // I: 발신 기관이 시군구가 아님. 기관 표시가 아예 없는 NONE 은 포함하지 않는다(기관이 시군구가 아니라고 말할 근거가 없다)
            case I -> pair.a().agencyKind() == AgencyKind.NON_SIGUNGU || pair.b().agencyKind() == AgencyKind.NON_SIGUNGU;
        };
    }

    private static boolean isOldIncheon(AlertFacts facts) {
        return facts.sigunguId().map(OLD_INCHEON_SIGUNGU_IDS::contains).orElse(false);
    }
}
