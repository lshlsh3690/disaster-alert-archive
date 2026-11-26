package com.disaster.alert.alertapi.domain.event.judge;

import java.util.List;
import java.util.Optional;

/**
 * 판사 프롬프트 버전별 모델·단가를 한 곳에 모은 목록(순수 클래스, JPA·Spring 없음).
 * 판사·평가 도구·관리자 대시보드가 같은 값을 읽게 해서, 모델을 바꿀 때 한 곳만 고치도록 한다.
 *
 * <p><b>일치율은 여기 없다</b>(2026-11-26, specs/014). 버전별 272쌍 일치율은 DB 측정 기록({@code judge_agreement_run}·
 * {@code judge_agreement_run_pair})의 쌍별 판정과 현재 사람 라벨로 그때그때 센다({@code JudgeAgreementService}). 예전에는 이
 * 목록에 숫자로 적었는데, 라벨을 고쳐도 숫자가 그대로라 손으로 다시 세어 고쳐야 했다. v1·v2 측정 근거(리포트·커밋·토큰)는
 * {@code V146__seed_judge_agreement_v1_v2.sql} 머리 주석에 있다.
 *
 * <p>버전을 올릴 때는 맨 앞에 새 항목을 추가하고 옛 항목은 지우지 않는다 — 이미 그 버전으로 채점된 날의 모델·비용을 계속
 * 보여 줘야 한다. 새 버전은 {@code --eval.judge-agreement} 로 272쌍을 재서 측정 기록을 DB 에 남겨야 일일 채점이 돈다.
 *
 * <p>모델을 고른 이유: v2(gpt-4o-mini, 2026-11-18)는 v1(gpt-4o)과 272쌍 일치율이 전체로 비슷하고(사람과 반대로 판정한 쌍
 * 22 → 21) 쌍당 비용이 약 1/16 이라, 큰 사건 날 수십 달러가 들던 일일 채점을 그대로 돌리려고 바꿨다. 대신 틀리는 방향이
 * 바뀌었다 — 같은 사건 쌍은 더 잘 맞추고 다른 사건 쌍은 덜 맞춘다(사람 "다른 사건"을 "같은 사건"으로 보는 쌍 3 → 7). 시스템이
 * 잘못 묶은 쌍을 같은 사건으로 보면 그 과병합을 놓치므로, v2 로 매긴 섞지 않은 비율은 실제보다 조금 높게 나올 수 있다.
 *
 * <p>단가(USD / 100만 토큰)는 OpenAI 공개 가격표 기준이며, 모델을 바꾸고 단가를 안 고쳐 리포트 비용이 16배로 찍힌
 * 적이 있어 모델과 한 항목에 묶었다.
 */
public final class JudgeVersionCatalog {

    // 최신 버전이 먼저다 — 관리자 화면의 판사 버전 이력 표가 이 순서 그대로 나간다
    private static final List<Entry> ENTRIES = List.of(
            new Entry("v2", "gpt-4o-mini", 0.15, 0.60),
            new Entry("v1", "gpt-4o", 2.50, 10.00));

    private JudgeVersionCatalog() {
    }

    /** 버전 하나. 단가는 USD / 100만 토큰이다. */
    public record Entry(String version, String model, double inputUsdPerMillion, double outputUsdPerMillion) {

        /** 이 버전 단가로 센 추정 비용(USD). 캐시 할인을 빼지 않은 상한값이다. 비용 식은 여기 한 곳에만 둔다. */
        public double estimateUsd(long promptTokens, long completionTokens) {
            return promptTokens * inputUsdPerMillion / 1_000_000.0
                    + completionTokens * outputUsdPerMillion / 1_000_000.0;
        }
    }

    /** 모든 버전, 최신 버전이 먼저. */
    public static List<Entry> all() {
        return ENTRIES;
    }

    /** 버전 이름으로 찾는다. 목록에 없으면 비어 있다. */
    public static Optional<Entry> find(String version) {
        return ENTRIES.stream().filter(e -> e.version().equals(version)).findFirst();
    }
}
