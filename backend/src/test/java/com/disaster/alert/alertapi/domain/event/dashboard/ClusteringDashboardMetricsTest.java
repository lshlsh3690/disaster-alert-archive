package com.disaster.alert.alertapi.domain.event.dashboard;

import com.disaster.alert.alertapi.domain.event.dashboard.ClusteringDashboardMetrics.DatedVersion;
import com.disaster.alert.alertapi.domain.event.dashboard.ClusteringDashboardMetrics.DayEntry;
import com.disaster.alert.alertapi.domain.event.dashboard.ClusteringDashboardMetrics.DayStatus;
import com.disaster.alert.alertapi.domain.event.dashboard.ClusteringDashboardMetrics.Ratio;
import com.disaster.alert.alertapi.domain.event.dashboard.ClusteringDashboardMetrics.VersionPeriod;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 관리자 대시보드 순수 계산 테스트. DB·Spring 없이 도는 단위 테스트다.
 */
class ClusteringDashboardMetricsTest {

    /**
     * 분모가 0 이면 백분율은 "없음"(null)이어야 한다. 0% 로 바꾸면 "하나도 못 맞춤"과 "계산할 대상이 없음"이
     * 구별되지 않아 관리자가 점수를 잘못 읽는다. 분모가 있으면 정상 백분율이 나온다.
     * Red 단계에서는 뼈대가 항상 percent 0.0 을 돌려줘서, 분모 0 에서 null 을, 분모 4 에서 75.0 을 기대하는 단언이 실패한다.
     */
    @Test
    void 분모가_0이면_백분율이_null이고_분모가_있으면_백분율이_나온다() {
        // 실행: 분모 0(판정할 쌍이 없는 날)과 분모 4(3/4 = 75%)
        Ratio empty = ClusteringDashboardMetrics.ratio(0, 0);
        Ratio some = ClusteringDashboardMetrics.ratio(3, 4);

        // 검증: 분모 0 은 계산할 수 없음(null) — 0% 가 아니다. 분자·분모는 그대로 들고 있다
        assertThat(empty.percent()).isNull();
        assertThat(empty.denominator()).isZero();
        // 검증: 분모가 있으면 분자/분모 x 100
        assertThat(some.percent()).isEqualTo(75.0);
    }

    /**
     * 요청 범위의 모든 날이 최신 먼저로 나오고, 상태가 세 가지로 구별되어야 한다. 기록이 없는 날을 건너뛰면 "채점이 꺼진 날"이
     * 표에서 사라져 구멍을 알아챌 수 없다. 기록은 있지만 판정이 0쌍인 날은 "기록 없음"과 다른 원인이라 따로 보여야 한다.
     * Red 단계에서는 뼈대가 빈 목록을 돌려줘서 3일이 나온다는 단언에서 실패한다.
     */
    @Test
    void 범위의_모든_날이_나오고_기록없음_판정0쌍_채점됨이_구별된다() {
        // 준비: 11-15 ~ 11-17 세 날. 11-17 은 6쌍 판정, 11-16 은 기록은 있으나 0쌍, 11-15 는 기록 자체가 없음
        Map<LocalDate, Integer> pairCountByDate = Map.of(
                LocalDate.of(2026, 11, 17), 6,
                LocalDate.of(2026, 11, 16), 0);

        // 실행: 범위를 채운다
        List<DayEntry> days = ClusteringDashboardMetrics.fillDays(
                LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 17), pairCountByDate);

        // 검증: 세 날 모두, 최신 먼저(계약의 날짜 내림차순). 키가 없는 날은 NO_RECORD, 값 0 은 NO_PAIRS
        assertThat(days).containsExactly(
                new DayEntry(LocalDate.of(2026, 11, 17), DayStatus.SCORED),
                new DayEntry(LocalDate.of(2026, 11, 16), DayStatus.NO_PAIRS),
                new DayEntry(LocalDate.of(2026, 11, 15), DayStatus.NO_RECORD));
    }

    /**
     * 추정 비용 = 프롬프트 토큰 x 입력 단가 / 100만 + 완성 토큰 x 출력 단가 / 100만, 단가는 그날 판사 버전 것이다.
     * 단가를 모르는 버전은 0 원으로 얼버무리지 않고 "알 수 없음"(empty)이어야 한다 — 실제 비용이 없는 것과 모르는 것은 다르다.
     * Red 단계에서는 뼈대가 항상 empty 를 돌려줘서 v2 비용을 기대하는 첫 단언에서 실패한다.
     */
    @Test
    void 추정_비용은_토큰_곱하기_그_버전_단가이고_모르는_버전은_비어_있다() {
        // 실행: v2(gpt-4o-mini 입력 0.15 / 출력 0.60 USD per 1M)로 하루치 토큰 — 9305 / 12 는 실제 하루 채점 규모
        Optional<Double> v2Cost = ClusteringDashboardMetrics.estimatedCostUsd("v2", 9305, 12);
        Optional<Double> unknownCost = ClusteringDashboardMetrics.estimatedCostUsd("v999", 9305, 12);

        // 검증: 9305 x 0.15 / 1M + 12 x 0.60 / 1M = 0.00139575 + 0.0000072 = 0.00140295 (부동소수 오차만 허용)
        assertThat(v2Cost).isPresent();
        assertThat(v2Cost.get()).isCloseTo(0.00140295, within(1e-9));
        // 검증: 목록에 없는 버전은 비용을 알 수 없다
        assertThat(unknownCost).isEmpty();
    }

    /**
     * 판사 버전마다 첫날·마지막 날·채점 일수가 나와야 한다 — 버전 이력 표가 "이 버전이 언제부터 언제까지 쓰였나"를 보여 준다.
     * 입력은 일부러 날짜 순서를 섞었다(저장소가 정렬해서 준다고 가정하지 않는다). 기록이 없는 버전은 결과에 없다.
     * Red 단계에서는 뼈대가 빈 맵을 돌려줘서 v2 기간을 기대하는 단언에서 실패한다.
     */
    @Test
    void 버전별_첫날_마지막날_채점_일수가_나오고_기록_없는_버전은_없다() {
        // 준비: v1 은 11-17 하루, v2 는 11-18~11-20 사흘. 순서는 섞음
        List<DatedVersion> records = List.of(
                new DatedVersion(LocalDate.of(2026, 11, 20), "v2"),
                new DatedVersion(LocalDate.of(2026, 11, 17), "v1"),
                new DatedVersion(LocalDate.of(2026, 11, 18), "v2"),
                new DatedVersion(LocalDate.of(2026, 11, 19), "v2"));

        // 실행
        Map<String, VersionPeriod> periods = ClusteringDashboardMetrics.versionPeriods(records);

        // 검증 v2: 첫날 11-18, 마지막 날 11-20, 사흘
        assertThat(periods.get("v2")).isEqualTo(
                new VersionPeriod("v2", LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 20), 3));
        // 검증 v1: 하루만 채점해서 첫날 = 마지막 날, 하루
        assertThat(periods.get("v1")).isEqualTo(
                new VersionPeriod("v1", LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 17), 1));
        // 검증: 채점 기록이 없는 버전은 결과에 없다(화면은 "채점 기록 없음")
        assertThat(periods).doesNotContainKey("v3");
    }
}
