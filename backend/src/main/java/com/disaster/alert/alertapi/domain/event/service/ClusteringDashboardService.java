package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.common.exception.CustomException;
import com.disaster.alert.alertapi.domain.common.exception.ErrorCode;
import com.disaster.alert.alertapi.domain.disasteralert.dto.AlertSummaryDto;
import com.disaster.alert.alertapi.domain.disasteralert.service.DisasterAlertService;
import com.disaster.alert.alertapi.domain.event.dashboard.ClusteringDashboardMetrics;
import com.disaster.alert.alertapi.domain.event.dashboard.ClusteringDashboardMetrics.DatedVersion;
import com.disaster.alert.alertapi.domain.event.dashboard.ClusteringDashboardMetrics.DayEntry;
import com.disaster.alert.alertapi.domain.event.dashboard.ClusteringDashboardMetrics.DayStatus;
import com.disaster.alert.alertapi.domain.event.dashboard.ClusteringDashboardMetrics.Ratio;
import com.disaster.alert.alertapi.domain.event.dashboard.ClusteringDashboardMetrics.VersionPeriod;
import com.disaster.alert.alertapi.domain.event.dashboard.DisagreementGrading;
import com.disaster.alert.alertapi.domain.event.dashboard.DisagreementGrading.GradedPair;
import com.disaster.alert.alertapi.domain.event.dashboard.DisagreementGrading.VersionGrade;
import com.disaster.alert.alertapi.domain.event.dto.ClusteringDashboardDailyChecksResponse;
import com.disaster.alert.alertapi.domain.event.dto.ClusteringDashboardDailyScoresResponse;
import com.disaster.alert.alertapi.domain.event.dto.ClusteringDashboardDisagreementsResponse;
import com.disaster.alert.alertapi.domain.event.dto.ClusteringDashboardHardPairGradesResponse;
import com.disaster.alert.alertapi.domain.event.dto.ClusteringDashboardJudgeVersionsResponse;
import com.disaster.alert.alertapi.domain.event.evaluation.PairCountResult;
import com.disaster.alert.alertapi.domain.event.judge.JudgeVersionCatalog;
import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyCheck;
import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyScore;
import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyScorePair;
import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyScorePair.Disagreement;
import com.disaster.alert.alertapi.domain.event.model.ClusteringPairLabel;
import com.disaster.alert.alertapi.domain.event.repository.ClusteringDailyCheckRepository;
import com.disaster.alert.alertapi.domain.event.repository.ClusteringDailyScorePairRepository;
import com.disaster.alert.alertapi.domain.event.repository.ClusteringDailyScoreRepository;
import com.disaster.alert.alertapi.domain.event.repository.ClusteringPairLabelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 관리자 대시보드 조회 service - 일일 채점·구조 검사 기록을 읽어 {@link ClusteringDashboardMetrics} 계산과 합친다.
 *
 * <p>읽기 전용이다(기존 테이블만 읽고 LLM 도 부르지 않는다). 비율·비용·기간·빈 날짜 규칙은 순수 계산 클래스와
 * {@link PairCountResult}, {@link JudgeVersionCatalog} 에만 있고 여기서는 저장된 값을 그쪽에 넘기기만 한다 —
 * 같은 정의를 이 service 에 다시 쓰면 대시보드와 일일 채점 점수가 어긋난다. 알림 원문은 다른 맥락(disasteralert)의
 * 저장소를 직접 주입하지 않고 {@link DisasterAlertService} 를 거쳐 읽는다(research R5).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClusteringDashboardService {

    // 하루 1행이라 1년도 가볍지만, 상한이 없으면 실수로 아주 넓은 범위를 요청할 수 있다(research R6)
    private static final int MAX_RANGE_DAYS = 366;
    private static final int MAX_PAGE_SIZE = 100;

    private final ClusteringDailyScoreRepository scoreRepository;
    private final ClusteringDailyScorePairRepository pairRepository;
    private final ClusteringDailyCheckRepository checkRepository;
    private final ClusteringPairLabelRepository labelRepository;
    private final DisasterAlertService disasterAlertService;

    /**
     * 날짜별 일일 채점. 범위의 모든 날을 최신 날짜 먼저로 내고, 기록 없는 날은 날짜와 상태(NO_RECORD)만 채운다.
     *
     * @throws CustomException 범위가 잘못되면 {@code CLUSTERING_DASHBOARD_RANGE_INVALID}
     */
    public ClusteringDashboardDailyScoresResponse dailyScores(LocalDate from, LocalDate to) {
        // 1) 범위부터 검증한다 — 아래 빈 날 채우기가 범위 길이만큼 객체를 만들기 때문이다
        validateRange(from, to);
        // 2) 범위의 채점 기록을 날짜로 찾을 수 있게 모은다(없는 날은 키가 없다)
        Map<LocalDate, ClusteringDailyScore> scoreByDate = scoreRepository.findByScoreDateBetween(from, to).stream()
                .collect(Collectors.toMap(ClusteringDailyScore::getScoreDate, Function.identity()));
        Map<LocalDate, Integer> pairCountByDate = scoreByDate.values().stream()
                .collect(Collectors.toMap(ClusteringDailyScore::getScoreDate, ClusteringDailyScore::getPairCount));
        // 3) 빈 날을 채운 날짜 칸마다 기록이 있으면 값을 채운다
        List<ClusteringDashboardDailyScoresResponse.Day> days = ClusteringDashboardMetrics
                .fillDays(from, to, pairCountByDate).stream()
                .map(entry -> toScoreDay(entry, scoreByDate.get(entry.date())))
                .toList();
        return new ClusteringDashboardDailyScoresResponse(from, to, days);
    }

    /**
     * 불일치 쌍(과병합·과소병합) 한 페이지. 정렬은 날짜 내림차순 → 쌍 id 오름차순이다.
     * 두 알림 원문과 사람 라벨은 한 페이지분을 한 번에 읽어 채운다.
     *
     * @param kind null 이면 두 종류 모두
     * @param unlabeledOnly true 면 사람 라벨이 없는 쌍만
     * @throws CustomException 범위·page·size 가 잘못되면 400 (범위는 {@code CLUSTERING_DASHBOARD_RANGE_INVALID})
     */
    public ClusteringDashboardDisagreementsResponse disagreements(LocalDate from, LocalDate to, Disagreement kind,
                                                                  boolean unlabeledOnly, int page, int size) {
        // 1) 입력 검증 — size 상한이 없으면 한 번에 수천 쌍(큰 재난 날)을 내려보낼 수 있다
        validateRange(from, to);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "page 는 0 이상, size 는 1~" + MAX_PAGE_SIZE);
        }
        // 2) kind 생략 = 두 종류. 맞춘 쌍(disagreement null)은 어느 쪽이든 조회에서 빠진다
        Set<Disagreement> kinds = kind == null ? EnumSet.allOf(Disagreement.class) : EnumSet.of(kind);
        Sort sort = Sort.by(Sort.Direction.DESC, "scoreDate").and(Sort.by(Sort.Direction.ASC, "id"));
        PageRequest pageable = PageRequest.of(page, size, sort);
        Page<ClusteringDailyScorePair> result = unlabeledOnly
                ? pairRepository.findUnlabeledDisagreements(from, to, kinds, pageable)
                : pairRepository.findByScoreDateBetweenAndDisagreementIn(from, to, kinds, pageable);
        // 3) 한 페이지의 알림 id 를 모아 원문과 라벨을 한 번에 읽는다(쌍마다 읽으면 N+1)
        Set<Long> alertIds = new LinkedHashSet<>();
        result.forEach(pair -> {
            alertIds.add(pair.getAlertA());
            alertIds.add(pair.getAlertB());
        });
        Map<Long, AlertSummaryDto> summaries = disasterAlertService.findAlertSummaries(alertIds).stream()
                .collect(Collectors.toMap(AlertSummaryDto::id, Function.identity()));
        Map<PairKey, ClusteringPairLabel> labels = labelsOf(alertIds);
        List<ClusteringDashboardDisagreementsResponse.Pair> pairs = result.stream()
                .map(pair -> toPair(pair, summaries, labels.get(new PairKey(pair.getAlertA(), pair.getAlertB()))))
                .toList();
        return new ClusteringDashboardDisagreementsResponse(
                page, size, result.getTotalElements(), result.getTotalPages(), pairs);
    }

    /**
     * 어려운 쌍 판사 성적 — 지금 불일치인 쌍 중 사람 라벨이 있는 것을 판사 버전별로 센다.
     *
     * <p>같은 두 알림이 여러 날 불일치로 나오면 날짜마다 한 번씩 센다 — 날짜마다 판사가 따로 판정했기 때문이다.
     * 판정·집계 규칙은 {@link DisagreementGrading} 에 있고 여기서는 읽은 값을 넘기기만 한다.
     * 라벨이 없으면 labeledPairs 0, versions 빈 목록(화면은 "아직 라벨 없음").
     */
    public ClusteringDashboardHardPairGradesResponse hardPairGrades() {
        // 1) 날짜 → 그날 판사 버전. 쌍의 버전은 그 쌍을 채점한 날의 버전이다(research R6). 날짜당 1행이라 수백 행
        Map<LocalDate, String> versionByDate = scoreRepository.findAllProjectedBy().stream()
                .collect(Collectors.toMap(ClusteringDailyScoreRepository.DatedVersionView::getScoreDate,
                        ClusteringDailyScoreRepository.DatedVersionView::getJudgeVersion));
        // 2) 지금 불일치인 채점 쌍 중 라벨 있는 것을 (판사 버전, 라벨, 판사 판정, 시스템 같은 이벤트)로 바꾼다
        List<GradedPair> graded = pairRepository.findLabeledDisagreements().stream()
                .map(row -> new GradedPair(versionByDate.get(row.getScoreDate()), row.getLabel(),
                        row.getJudgeVerdict(), row.getSystemSameEvent()))
                .toList();
        // 3) 버전별 집계는 순수 클래스가 한다. labeledPairs 는 쌍×날짜 수(UNSURE 포함)
        List<ClusteringDashboardHardPairGradesResponse.Version> versions = DisagreementGrading.gradeByVersion(graded)
                .stream().map(ClusteringDashboardService::toVersion).toList();
        return new ClusteringDashboardHardPairGradesResponse(graded.size(), versions);
    }

    /**
     * 날짜별 구조 검사. 범위의 모든 날을 최신 먼저로 내고, 검사 기록이 없는 날은 recorded=false 만 채운다
     * (0건으로 지어내면 검사가 안 돈 날과 문제없는 날이 구별되지 않는다).
     *
     * @throws CustomException 범위가 잘못되면 {@code CLUSTERING_DASHBOARD_RANGE_INVALID}
     */
    public ClusteringDashboardDailyChecksResponse dailyChecks(LocalDate from, LocalDate to) {
        validateRange(from, to);
        Map<LocalDate, ClusteringDailyCheck> checkByDate = checkRepository.findByCheckDateBetween(from, to).stream()
                .collect(Collectors.toMap(ClusteringDailyCheck::getCheckDate, Function.identity()));
        // 날짜 칸(순서·범위)은 채점 표와 같은 fillDays 에서 받는다 — 두 표의 날짜 규칙이 갈리지 않게. 상태는 쓰지 않는다
        List<ClusteringDashboardDailyChecksResponse.Day> days = ClusteringDashboardMetrics
                .fillDays(from, to, Map.of()).stream()
                .map(entry -> toCheckDay(entry.date(), checkByDate.get(entry.date())))
                .toList();
        return new ClusteringDashboardDailyChecksResponse(from, to, days);
    }

    // 구조 검사 날짜 칸 하나. 기록 없으면 recorded=false 만
    private static ClusteringDashboardDailyChecksResponse.Day toCheckDay(LocalDate date, ClusteringDailyCheck check) {
        if (check == null) {
            return new ClusteringDashboardDailyChecksResponse.Day(date, false, null, null, null, null);
        }
        // attention = 미배정 또는 임베딩 없음이 하나라도 있는 날(관리자가 문제 있는 날을 바로 찾게)
        boolean attention = check.getUnassignedCount() > 0 || check.getNoEmbeddingCount() > 0;
        return new ClusteringDashboardDailyChecksResponse.Day(date, true, check.getIncidentCount(),
                check.getUnassignedCount(), check.getNoEmbeddingCount(), attention);
    }

    /**
     * 판사 버전 이력. 판사 버전 목록 순서(최신 먼저) 그대로, 버전마다 목록의 모델·일치율에 채점 기록에서 구한
     * 첫날·마지막 날·채점 일수를 붙인다. 채점 기록이 없는 버전은 날짜 null, 일수 0 이다.
     */
    public ClusteringDashboardJudgeVersionsResponse judgeVersions() {
        // 1) 채점 기록 전체에서 버전별 기간을 구한다 — 계산 규칙은 Metrics 에 있다
        List<DatedVersion> records = scoreRepository.findAllProjectedBy().stream()
                .map(row -> new DatedVersion(row.getScoreDate(), row.getJudgeVersion()))
                .toList();
        Map<String, VersionPeriod> periods = ClusteringDashboardMetrics.versionPeriods(records);
        // 2) 목록 순서대로 항목을 만든다
        List<ClusteringDashboardJudgeVersionsResponse.Version> versions = new ArrayList<>();
        for (JudgeVersionCatalog.Entry entry : JudgeVersionCatalog.all()) {
            VersionPeriod period = periods.get(entry.version());
            versions.add(new ClusteringDashboardJudgeVersionsResponse.Version(
                    entry.version(), entry.model(), entry.measuredOn(),
                    ClusteringDashboardMetrics.ratio(entry.sameNum(), entry.sameDen()),
                    ClusteringDashboardMetrics.ratio(entry.diffNum(), entry.diffDen()),
                    period == null ? null : period.firstDate(),
                    period == null ? null : period.lastDate(),
                    period == null ? 0 : period.scoredDays()));
        }
        // 3) 기록에는 있는데 목록에 없는 버전(이론상 없음)도 숨기지 않고 모델·일치율 없이 뒤에 붙인다
        for (String version : new TreeSet<>(periods.keySet())) {
            if (JudgeVersionCatalog.find(version).isPresent()) {
                continue;
            }
            VersionPeriod period = periods.get(version);
            versions.add(new ClusteringDashboardJudgeVersionsResponse.Version(
                    version, null, null, null, null, period.firstDate(), period.lastDate(), period.scoredDays()));
        }
        return new ClusteringDashboardJudgeVersionsResponse(versions);
    }

    // 조회 범위: 시작 <= 끝, 길이(양 끝 포함) <= 366일. 어기면 400
    private static void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new CustomException(ErrorCode.CLUSTERING_DASHBOARD_RANGE_INVALID, "시작 날짜가 끝 날짜보다 늦습니다.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new CustomException(ErrorCode.CLUSTERING_DASHBOARD_RANGE_INVALID,
                    "조회 범위는 최대 " + MAX_RANGE_DAYS + "일입니다.");
        }
    }

    // 날짜 칸 하나. 기록이 없으면 날짜와 상태만(나머지 null 은 JSON 에서 빠진다)
    private static ClusteringDashboardDailyScoresResponse.Day toScoreDay(DayEntry entry, ClusteringDailyScore s) {
        if (s == null) {
            return new ClusteringDashboardDailyScoresResponse.Day(entry.date(), DayStatus.NO_RECORD,
                    null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }
        // 1) 저장된 개수로 PairCountResult 를 만들어 비율·점수 정의를 그쪽 한 곳에서 가져온다
        //    (과소병합/과병합 비율과 제외 미배정 칸은 이 화면이 쓰지 않아 비운다)
        PairCountResult counts = new PairCountResult(s.getCorrectlyMerged(), s.getMissed(), s.getOverMerged(),
                s.getUnsureCount(), 0, OptionalDouble.empty(), OptionalDouble.empty(), List.of());
        //    분모도 PairCountResult 에서 꺼내 분자/분모와 백분율이 같은 정의에서 나오게 한다
        Ratio purity = ClusteringDashboardMetrics.ratio(counts.correctlyMerged(), counts.mergedPairs());
        Ratio completeness = ClusteringDashboardMetrics.ratio(counts.correctlyMerged(), counts.sameEventPairs());
        // 2) 판사: 일치율은 그날 기록에 복사된 당시 값, 모델만 현재 목록에서 찾는다
        ClusteringDashboardDailyScoresResponse.Judge judge = new ClusteringDashboardDailyScoresResponse.Judge(
                s.getJudgeVersion(),
                JudgeVersionCatalog.find(s.getJudgeVersion()).map(JudgeVersionCatalog.Entry::model).orElse(null),
                ClusteringDashboardMetrics.ratio(s.getAgreementSameNum(), s.getAgreementSameDen()),
                ClusteringDashboardMetrics.ratio(s.getAgreementDiffNum(), s.getAgreementDiffDen()));
        // 3) 단가를 모르는 버전이면 비용은 null(0 원으로 얼버무리지 않는다)
        Double cost = ClusteringDashboardMetrics
                .estimatedCostUsd(s.getJudgeVersion(), s.getPromptTokens(), s.getCompletionTokens()).orElse(null);
        Double score = ClusteringDashboardMetrics.roundPercent(counts.score());
        return new ClusteringDashboardDailyScoresResponse.Day(entry.date(), entry.status(),
                s.getIncidentCount(), s.getPairCount(),
                s.getCorrectlyMerged(), s.getOverMerged(), s.getMissed(),
                s.getUnsureCount(), s.getFormatFailureCount(),
                purity, completeness, score, judge, s.getPromptTokens(), s.getCompletionTokens(), cost);
    }

    // 쌍 하나의 응답. 라벨이 없으면 라벨 필드 셋 모두 null
    private static ClusteringDashboardDisagreementsResponse.Pair toPair(
            ClusteringDailyScorePair pair, Map<Long, AlertSummaryDto> summaries, ClusteringPairLabel label) {
        // wrongSide 는 판사·시스템 답이 반대인 불일치 쌍에서만 의미가 있다(이 목록은 불일치 쌍만 담는다). UNSURE 면 null
        DisagreementGrading.WrongSide wrongSide = label == null ? null
                : DisagreementGrading.wrongSide(label.getLabel(), pair.getJudgeVerdict(), pair.isSystemSameEvent());
        return new ClusteringDashboardDisagreementsResponse.Pair(
                pair.getScoreDate(), pair.getDisagreement(), pair.getJudgeVerdict(),
                summaryOf(pair.getAlertA(), summaries), summaryOf(pair.getAlertB(), summaries),
                pair.getEventA(), pair.getEventB(),
                label == null ? null : label.getLabel(), label == null ? null : label.getCriteriaDate(), wrongSide);
    }

    // 한 페이지의 알림 id 로 라벨을 한 번에 읽는다. alertA 로만 거르므로 alertB 가 다른 줄은 쌍 키로 구분된다
    private Map<PairKey, ClusteringPairLabel> labelsOf(Set<Long> alertIds) {
        if (alertIds.isEmpty()) {
            return Map.of();
        }
        return labelRepository.findByAlertAIn(alertIds).stream()
                .collect(Collectors.toMap(l -> new PairKey(l.getAlertA(), l.getAlertB()), Function.identity()));
    }

    private static ClusteringDashboardHardPairGradesResponse.Version toVersion(VersionGrade g) {
        return new ClusteringDashboardHardPairGradesResponse.Version(
                g.version(), g.model(), g.judgeRight(), g.systemRight(), g.unsureCount());
    }

    // (alertA, alertB) — alertA 가 작은 id 라 순서가 이미 정해져 있다
    private record PairKey(long alertA, long alertB) {
    }

    // 알림을 못 찾으면(지워졌거나 아직 없음) id 만 채운다 — 화면이 "원문 없음"을 보인다
    private static ClusteringDashboardDisagreementsResponse.AlertSummary summaryOf(
            long alertId, Map<Long, AlertSummaryDto> summaries) {
        AlertSummaryDto summary = summaries.get(alertId);
        return summary == null
                ? new ClusteringDashboardDisagreementsResponse.AlertSummary(alertId, null, null, null)
                : new ClusteringDashboardDisagreementsResponse.AlertSummary(
                        alertId, summary.sentAt(), summary.type(), summary.message());
    }
}
