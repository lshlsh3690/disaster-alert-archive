package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.AlertFacts;
import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.PairFacts;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Red 단계 뼈대 — 구현은 Green 에서
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
        /** 한쪽이라도 발신 기관이 인천 옛 구(남동구·동구·서구 개편 전 코드) */
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

    /**
     * 요청한 규칙마다 조건에 걸린 쌍을 모아 돌려준다. 한 쌍이 여러 규칙에 걸리면 각 규칙 목록에 모두 들어간다.
     * 요청한 규칙은 걸린 쌍이 없어도 빈 목록으로 키가 남는다.
     */
    public Map<Rule, List<PairInput>> find(Set<Rule> rules, List<PairInput> pairs) {
        Map<Rule, List<PairInput>> result = new EnumMap<>(Rule.class);
        for (Rule rule : rules) {
            result.put(rule, new ArrayList<>());
        }
        return result;
    }
}
