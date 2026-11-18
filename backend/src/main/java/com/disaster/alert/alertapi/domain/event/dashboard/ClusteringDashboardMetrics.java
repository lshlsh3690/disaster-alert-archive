package com.disaster.alert.alertapi.domain.event.dashboard;

import com.disaster.alert.alertapi.domain.event.judge.JudgeVersionCatalog;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 관리자 대시보드가 쓰는 순수 계산 모음(JPA·Spring 없음) — 비율, 빈 날짜 채우기, 추정 비용, 판사 버전 기간.
 * service 는 저장소에서 읽은 값을 여기에 넘기기만 하고 계산 규칙은 이 클래스에만 둔다.
 *
 * <p>점수·맞게 묶은 비율은 여기서 다시 정의하지 않는다 — 그 계산은 {@code evaluation/PairCountResult} 한 곳이고,
 * 같은 식을 두 군데 두면 대시보드와 일일 채점 점수가 어긋난다.
 */
public final class ClusteringDashboardMetrics {


    private ClusteringDashboardMetrics() {
    }

    /** 비율. 분자·분모를 그대로 들고, 분모가 0 이면 percent 는 null(= 계산할 수 없음). */
    public record Ratio(int numerator, int denominator, Double percent) {
    }

    /** 날짜 칸 상태. SCORED = 판정 1쌍 이상, NO_PAIRS = 기록은 있으나 판정 0쌍, NO_RECORD = 그날 기록 없음. */
    public enum DayStatus {
        SCORED, NO_PAIRS, NO_RECORD
    }

    /** 날짜 하나와 그 상태. */
    public record DayEntry(LocalDate date, DayStatus status) {
    }

    /** 채점 기록 한 건에서 날짜와 판사 버전만 뽑은 값. */
    public record DatedVersion(LocalDate date, String version) {
    }

    /** 버전 하나의 채점 기간. scoredDays 는 그 버전으로 채점한 날 수. */
    public record VersionPeriod(String version, LocalDate firstDate, LocalDate lastDate, int scoredDays) {
    }

    /**
     * 분자/분모로 비율을 만든다. 분모 0 이면 percent 는 null 이고 0% 로 바꾸지 않는다 —
     * "하나도 못 맞춤"과 "계산할 대상이 없음"을 관리자가 구별해야 하기 때문이다.
     * 백분율은 소수 첫째 자리까지 반올림한다(판사 일치율 리포트의 {@code %.1f} 표기와 맞춘다).
     */
    public static Ratio ratio(int numerator, int denominator) {
        // 1) 분모 0: 나눗셈 자체를 하지 않고 percent 를 비운다
        if (denominator == 0) {
            return new Ratio(numerator, denominator, null);
        }
        // 2) 분자·분모는 그대로 두고 표시용 백분율만 반올림한다
        double percent = Math.round(numerator * 1000.0 / denominator) / 10.0;
        return new Ratio(numerator, denominator, percent);
    }

    /**
     * from~to(양 끝 포함)의 모든 날을 최신 날짜 먼저로 만들고 날짜별 상태를 붙인다.
     * 기록 없는 날을 건너뛰면 "채점이 꺼진 날"이 표에서 사라져 구멍을 알 수 없다.
     *
     * @param pairCountByDate 기록이 있는 날 → 그날 판정 쌍 수. 키가 없는 날은 기록 없음이다.
     */
    public static List<DayEntry> fillDays(LocalDate from, LocalDate to, Map<LocalDate, Integer> pairCountByDate) {
        List<DayEntry> days = new ArrayList<>();
        // 1) 최신(to)에서 과거(from)로 내려가며 채운다 — 화면이 이 순서 그대로 쓴다
        for (LocalDate date = to; !date.isBefore(from); date = date.minusDays(1)) {
            Integer pairCount = pairCountByDate.get(date);
            // 2) 키 없음 = 기록 없음, 값 0 = 기록은 있으나 판정 0쌍, 1 이상 = 채점됨
            DayStatus status = pairCount == null ? DayStatus.NO_RECORD
                    : pairCount == 0 ? DayStatus.NO_PAIRS : DayStatus.SCORED;
            days.add(new DayEntry(date, status));
        }
        return days;
    }

    /**
     * 그날 판사 버전의 단가 × 토큰으로 추정한 비용(USD, 상한 — 프롬프트 캐시 할인은 반영하지 않는다).
     * 목록에 없는 버전이면 비어 있다 — 단가를 모르는 것을 0 원으로 얼버무리지 않는다.
     */
    public static Optional<Double> estimatedCostUsd(String version, long promptTokens, long completionTokens) {
        return JudgeVersionCatalog.find(version).map(entry -> entry.estimateUsd(promptTokens, completionTokens));
    }

    /**
     * 채점 기록들에서 버전별 첫날·마지막 날·채점 일수를 구한다.
     * 결과에 없는 버전은 채점 기록이 없는 버전이다. 입력 순서는 가정하지 않는다.
     * 같은 날이 같은 버전으로 두 번 들어와도 하루로 센다.
     */
    public static Map<String, VersionPeriod> versionPeriods(List<DatedVersion> records) {
        // 1) 버전별로 날짜를 모은다
        Map<String, List<LocalDate>> datesByVersion = new HashMap<>();
        for (DatedVersion record : records) {
            datesByVersion.computeIfAbsent(record.version(), v -> new ArrayList<>()).add(record.date());
        }
        // 2) 버전마다 최소·최대 날짜와 서로 다른 날 수를 계산한다
        Map<String, VersionPeriod> periods = new HashMap<>();
        datesByVersion.forEach((version, dates) -> periods.put(version, new VersionPeriod(
                version,
                dates.stream().min(Comparator.naturalOrder()).orElseThrow(),
                dates.stream().max(Comparator.naturalOrder()).orElseThrow(),
                (int) dates.stream().distinct().count())));
        return periods;
    }
}
