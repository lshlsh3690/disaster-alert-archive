package com.disaster.alert.alertapi.domain.event.dashboard;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 관리자 대시보드가 쓰는 순수 계산 모음(JPA·Spring 없음) — 비율, 빈 날짜 채우기, 추정 비용, 판사 버전 기간.
 * service 는 저장소에서 읽은 값을 여기에 넘기기만 하고 계산 규칙은 이 클래스에만 둔다.
 *
 * <p>// Red 단계 뼈대 — 구현은 Green 에서
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

    /** 분자/분모로 비율을 만든다. 분모 0 이면 percent 는 null 이고 0% 로 바꾸지 않는다. */
    public static Ratio ratio(int numerator, int denominator) {
        return new Ratio(numerator, denominator, 0.0);
    }

    /**
     * from~to(양 끝 포함)의 모든 날을 최신 날짜 먼저로 만들고 날짜별 상태를 붙인다.
     *
     * @param pairCountByDate 기록이 있는 날 → 그날 판정 쌍 수. 키가 없는 날은 기록 없음이다.
     */
    public static List<DayEntry> fillDays(LocalDate from, LocalDate to, Map<LocalDate, Integer> pairCountByDate) {
        return List.of();
    }

    /** 그날 판사 버전의 단가 × 토큰으로 추정한 비용(USD, 상한). 목록에 없는 버전이면 비어 있다. */
    public static Optional<Double> estimatedCostUsd(String version, long promptTokens, long completionTokens) {
        return Optional.empty();
    }

    /**
     * 채점 기록들에서 버전별 첫날·마지막 날·채점 일수를 구한다.
     * 결과에 없는 버전은 채점 기록이 없는 버전이다.
     */
    public static Map<String, VersionPeriod> versionPeriods(List<DatedVersion> records) {
        return Map.of();
    }
}
