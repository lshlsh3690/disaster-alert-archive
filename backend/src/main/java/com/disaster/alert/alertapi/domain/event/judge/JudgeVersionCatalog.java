package com.disaster.alert.alertapi.domain.event.judge;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 판사 프롬프트 버전별 모델·272쌍 일치율·단가를 한 곳에 모은 목록(순수 클래스, JPA·Spring 없음).
 * 판사·리포트·관리자 대시보드가 같은 값을 읽게 해서, 모델을 바꿀 때 한 곳만 고치도록 한다.
 *
 * <p>버전을 올릴 때는 맨 앞에 새 항목을 추가하고 옛 항목은 지우지 않는다 — 옛 버전 값은 비교 기준이고,
 * 이미 그 버전으로 채점된 날의 비용·신뢰도를 계속 보여 줘야 한다. 일치율은 {@code --eval.judge-agreement} 로
 * 사람 라벨과 판사 판정을 견준 값이다. 온도 0이어도 답이 매번 같지는 않아 다시 재면 한두 쌍은 달라질 수 있다.
 *
 * <p>v1(gpt-4o): 2026-11-17 측정(227쌍, $0.864)을 같은 날 라벨 재확인 두 번(D·E·G·I 55쌍, 원칙 3 의심 3쌍) 반영해
 * 다시 센 값. 근거는 {@code backend/evaluation/reports/judge-agreement-2026-11-17.md}. 판사가 사람보다 잘게 나누는 쪽
 * (사람 같은 사건·판사 다른 사건이 대부분)이라 일일 점수의 과병합은 실제보다 많게 나올 수 있다.
 *
 * <p>v2(gpt-4o-mini): 2026-11-18 같은 프롬프트·같은 라벨로 225쌍을 판정한 값($0.051). 근거는
 * {@code backend/evaluation/reports/judge-agreement-2026-11-18-mini.md}. <b>모델을 gpt-4o 에서 바꾼 이유</b>: 272쌍
 * 일치율이 전체로 비슷하고(사람과 반대로 판정한 쌍 22 → 21) 쌍당 비용이 약 1/16 이라, 큰 사건 날 수십 달러가 들던
 * 일일 채점을 그대로 돌리려는 선택이다. 대신 틀리는 방향이 바뀌었다 — 같은 사건 쌍은 더 잘 맞추고(87.3% → 90.1%)
 * 다른 사건 쌍은 덜 맞춘다(96.3% → 91.4%, 사람 "다른 사건"을 "같은 사건"으로 보는 쌍 3 → 7). 시스템이 잘못 묶은
 * 쌍을 같은 사건으로 보면 그 과병합을 놓치므로, v2 로 매긴 맞게 묶은 비율은 실제보다 조금 높게 나올 수 있다.
 * 모델이나 프롬프트를 바꾸면 항목을 추가하고 272쌍으로 다시 잰다(FR-018).
 *
 * <p>단가(USD / 100만 토큰)는 OpenAI 공개 가격표 기준이며, 모델을 바꾸고 단가를 안 고쳐 리포트 비용이 16배로 찍힌
 * 적이 있어 모델과 한 항목에 묶었다.
 */
public final class JudgeVersionCatalog {

    // 최신 버전이 먼저다 — 관리자 화면의 판사 버전 이력 표가 이 순서 그대로 나간다
    private static final List<Entry> ENTRIES = List.of(
            new Entry("v2", "gpt-4o-mini", 127, 141, 74, 81, 0.15, 0.60, LocalDate.of(2026, 11, 18)),
            new Entry("v1", "gpt-4o", 124, 142, 77, 80, 2.50, 10.00, LocalDate.of(2026, 11, 17)));

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

    /** 버전 이름으로 찾는다. 목록에 없으면 비어 있다(= 일일 채점을 하지 않는 버전). */
    public static Optional<Entry> find(String version) {
        return ENTRIES.stream().filter(e -> e.version().equals(version)).findFirst();
    }
}
