package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.event.evaluation.AssignmentReader;
import com.disaster.alert.alertapi.domain.event.evaluation.AlertSnapshot;
import com.disaster.alert.alertapi.domain.event.evaluation.JudgeInputAssembler;
import com.disaster.alert.alertapi.domain.event.evaluation.JudgeInputAssembler.Session;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.evaluation.PairCountResult;
import com.disaster.alert.alertapi.domain.event.evaluation.PairCounter;
import com.disaster.alert.alertapi.domain.event.evaluation.PairCounter.Observation;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge.Agreement;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge.JudgeResult;
import com.disaster.alert.alertapi.domain.event.judge.JudgeVerdict;
import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyScore;
import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyScorePair;
import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyScorePair.Disagreement;
import com.disaster.alert.alertapi.domain.event.repository.ClusteringDailyScorePairRepository;
import com.disaster.alert.alertapi.domain.event.repository.ClusteringDailyScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 하루치 사건 알림을 판사(LLM)로 채점해 {@code clustering_daily_score}·{@code clustering_daily_score_pair} 에 남긴다
 * (설계: specs/006 plan 4절, 결정 R4~R7).
 *
 * <p>사람 라벨 272쌍은 정해진 틀에서 한 번 뽑은 표본이라 운영 전체를 대표하지 못한다. 그래서 매일 운영 쌍을 판사에게 묻고,
 * 판사 판정을 라벨로 보고 시스템 묶음이 맞았는지 센다. 판사 일치율(272쌍)은 점수와 함께 저장해 점수를 얼마나 믿을지 남긴다.
 *
 * <p>스케줄러({@code ClusteringDailyScoreScheduler})가 기록 없는 날마다 {@link #score} 를 부른다. <b>유료 호출</b>이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusteringDailyScoreService {

    // 판정 쌍의 간격 상한(시간). 평가 셋을 뽑은 틀(168시간)과 같아야 272쌍 일치율을 운영 쌍에 붙일 수 있다(R4)
    private static final int WINDOW_HOURS = 168;
    // 판정이 오래 걸리는 하루치라 멈춘 건지 알 수 있게 이 간격마다 진행을 남긴다
    private static final int PROGRESS_INTERVAL = 100;

    private static final String COUNT_INCIDENTS = """
            SELECT COUNT(*) FROM disaster_alert
            WHERE created_at >= ? AND created_at < ? AND alert_kind = 'INCIDENT'
            """;

    // 판정 대상 쌍(spec FR-016, research R4). x = 그날 사건 알림, y = x 보다 앞선 168시간 안의 사건 알림(x 와 같은 날 알림 포함).
    //  ① 시스템이 같은 이벤트에 넣은 쌍: 유형·지역 조건 없음 - 클러스터링 후보 검색이 유형을 보지 않아 유형이 다른 알림도
    //     한 이벤트에 들어갈 수 있고, 그 과병합을 점수에서 놓치지 않으려는 것이다
    //  ② 다른 이벤트에 있는 쌍: 같은 유형이고 수신 시군구를 공유하거나 region_adjacency 이웃인 경우만(두 알림 모두 이벤트에 배정돼 있어야 함)
    // region 은 수신 시군구(법정동 코드 앞 5자리)이고, 시도 전체 선택(xx000)은 어느 시군구도 뜻하지 않아 뺀다.
    // (y.created_at, y.id) < (x.created_at, x.id) 는 같은 시각이어도 한 방향만 잡는 순서다. 결과는 (작은 id, 큰 id)로
    // 정규화하고 DISTINCT 로 겹침을 없애 같은 쌍을 두 번 판정(유료)하지 않는다. event_a/event_b 는 정규화된 순서를 따른다.
    private static final String PAIRS_SQL = """
            WITH x AS (
                SELECT disaster_alert_id AS id, created_at, disaster_type, event_id
                FROM disaster_alert
                WHERE created_at >= ? AND created_at < ? AND alert_kind = 'INCIDENT'
            ),
            y AS (
                SELECT disaster_alert_id AS id, created_at, disaster_type, event_id
                FROM disaster_alert
                WHERE created_at >= ? AND created_at < ? AND alert_kind = 'INCIDENT'
            ),
            region AS (
                SELECT DISTINCT r.disaster_alert_id AS id, LEFT(r.legal_district_code, 5) AS sgg
                FROM disaster_alert_region r
                JOIN y ON y.id = r.disaster_alert_id
                WHERE SUBSTRING(r.legal_district_code FROM 3 FOR 3) <> '000'
            )
            SELECT DISTINCT
                LEAST(x.id, y.id) AS alert_a,
                GREATEST(x.id, y.id) AS alert_b,
                CASE WHEN x.id < y.id THEN x.event_id ELSE y.event_id END AS event_a,
                CASE WHEN x.id < y.id THEN y.event_id ELSE x.event_id END AS event_b
            FROM x
            JOIN y ON (y.created_at, y.id) < (x.created_at, x.id)
                  AND y.created_at >= x.created_at - make_interval(hours => ?)
            WHERE (x.event_id IS NOT NULL AND x.event_id = y.event_id)
               OR (x.event_id IS NOT NULL AND y.event_id IS NOT NULL AND x.event_id <> y.event_id
                   AND y.disaster_type = x.disaster_type
                   AND (EXISTS (SELECT 1 FROM region rx JOIN region ry ON ry.sgg = rx.sgg
                                WHERE rx.id = x.id AND ry.id = y.id)
                        OR EXISTS (SELECT 1 FROM region rx
                                   JOIN region_adjacency a ON a.region_code = rx.sgg
                                   JOIN region ry ON ry.sgg = a.neighbor_code
                                   WHERE rx.id = x.id AND ry.id = y.id)))
            ORDER BY alert_a, alert_b
            """;

    /** 판정 대상 쌍 하나. 이벤트 id 는 채점 시점의 배정이며 alertA < alertB 순서를 따른다. */
    private record TargetPair(long alertA, long alertB, Long eventA, Long eventB) {
        boolean systemSameEvent() {
            return eventA != null && eventA.equals(eventB);
        }
    }

    /** 대상 쌍과 판사 결과(판정·토큰). */
    private record JudgedPair(TargetPair pair, JudgeResult result) {
        JudgeVerdict verdict() {
            return result.verdict();
        }
    }

    private final JdbcTemplate jdbcTemplate;
    private final AssignmentReader assignmentReader;
    private final JudgeInputAssembler inputAssembler;
    private final ClusteringJudge judge;
    private final ClusteringDailyScoreRepository scoreRepository;
    private final ClusteringDailyScorePairRepository pairRepository;
    private final TransactionTemplate transactionTemplate;
    private final JudgeAgreementService agreementService;

    /**
     * 그날(created_at 이 그날 00:00 이상, 다음날 00:00 미만) 발송된 사건 알림을 채점해 저장한다.
     *
     * <p>날짜는 시간대 변환 없이 {@code created_at} 컬럼 값과 바로 견준다 - 이 컬럼은 KST 벽시계 시각으로 저장되고
     * 일일 구조 검사({@code ClusteringDailyCheckService})도 같은 방식으로 날짜를 자른다.
     *
     * <p>판사 호출이 하나라도 예외를 내면 예외를 그대로 올리고 <b>아무것도 저장하지 않는다</b> - 일부만 저장하면 그날 점수가
     * 틀린 값으로 남고, 기록이 없어야 다음 실행이 그날을 다시 채점한다(R7). 이미 쓴 호출 비용은 버려진다.
     * 판사 버전의 일치율을 DB 측정 기록에서 셀 수 없으면(측정 없음, 셋 라벨 0행) 판사를 부르지 않고 warn 만 남긴다(R6, specs/014).
     * 일치율은 채점하는 순간 현재 사람 라벨로 센 값을 그날 행에 복사한다 — 뒤에 라벨을 고쳐도 이미 저장된 날은 바뀌지 않는다.
     *
     * @param date 채점할 날
     */
    public void score(LocalDate date) {
        // 1) 일치율을 셀 수 없는 판사 버전은 채점하지 않는다 - 점수 옆에 붙일 신뢰도가 없다(R6). 판사를 부르기 전에 막아야 돈이 안 나간다.
        //    측정 기록이 없거나 셋 라벨이 이 DB 에 없으면(운영에 라벨 덤프를 넣기 전) 비어 있다. 행을 남기지 않아 다음 실행이 다시 채점한다
        Optional<Agreement> agreement = agreementService.latest(ClusteringJudge.PROMPT_VERSION)
                .map(JudgeAgreementService.VersionAgreement::agreement);
        if (agreement.isEmpty()) {
            log.warn("클러스터링 일일 채점 건너뜀: 판사 버전 {} 의 일치율을 셀 수 없다(측정 기록 또는 평가 셋 라벨 없음) (date={})",
                    ClusteringJudge.PROMPT_VERSION, date);
            return;
        }

        // 2) 대상 쌍을 모은다. 여기까지는 DB 읽기뿐이라 쌍이 0개여도 "채점함" 행을 남길 수 있다
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        int incidentCount = jdbcTemplate.queryForObject(COUNT_INCIDENTS, Integer.class, from, to);
        List<TargetPair> targets = findTargetPairs(from, to);

        // 3) 판사를 쌍마다 부른다. 이 구간은 DB 트랜잭션 밖이다 - LLM 호출이 길어 커넥션을 잡고 있으면 안 된다
        List<JudgedPair> judged = judgeAll(date, targets);

        // 4) 점수를 세고, 다 끝난 뒤에야 한 트랜잭션으로 저장한다
        PairCountResult counted = count(judged);
        ClusteringDailyScore day = toDayRow(date, incidentCount, judged, counted, agreement.get());
        List<ClusteringDailyScorePair> pairRows = judged.stream().map(j -> toRow(date, j)).toList();
        transactionTemplate.executeWithoutResult(status -> {
            // 쌍 행이 일 행을 외래 키로 참조하므로 일 행을 먼저 밀어 넣는다(IDENTITY 쌍은 저장 즉시 INSERT)
            scoreRepository.saveAndFlush(day);
            pairRepository.saveAll(pairRows);
        });
        logSummary(date, day, counted);
    }

    private List<TargetPair> findTargetPairs(LocalDateTime from, LocalDateTime to) {
        // y 의 하한은 그날 시작 - 168시간: 그날 첫 알림의 168시간 전까지 볼 수 있게 한다
        return jdbcTemplate.query(PAIRS_SQL, (rs, i) -> new TargetPair(rs.getLong("alert_a"), rs.getLong("alert_b"),
                        nullableLong(rs.getLong("event_a"), rs.wasNull()), nullableLong(rs.getLong("event_b"), rs.wasNull())),
                from, to, from.minusHours(WINDOW_HOURS), to, WINDOW_HOURS);
    }

    private static Long nullableLong(long value, boolean wasNull) {
        return wasNull ? null : value;
    }

    // 쌍을 순차로 판정한다(동시 호출 이득이 작고 요금 한도 문제를 만들지 않으려는 선택 - JudgeAgreementRunner 와 같다).
    // 실패하면 어느 쌍인지 남기고 예외를 올려 그날 채점을 중단한다.
    private List<JudgedPair> judgeAll(LocalDate date, List<TargetPair> targets) {
        List<JudgedPair> judged = new ArrayList<>();
        if (targets.isEmpty()) {
            return judged;
        }
        // 판정 전에 그날 쌍 수를 남긴다 - 도중에 실패해도 그날이 몇 쌍짜리(얼마짜리)였는지 로그로 알 수 있게
        log.info("클러스터링 일일 채점 시작: {} 판정 대상 {}쌍", date, targets.size());
        Session inputs = inputAssembler.open();
        Map<Long, AlertSnapshot> snapshots = new HashMap<>();
        for (int i = 0; i < targets.size(); i++) {
            TargetPair pair = targets.get(i);
            AlertSnapshot first = snapshots.computeIfAbsent(pair.alertA(), assignmentReader::findAlert);
            AlertSnapshot second = snapshots.computeIfAbsent(pair.alertB(), assignmentReader::findAlert);
            JudgeResult result;
            try {
                result = judge.judge(inputs.toInput(first, second));
            } catch (RuntimeException e) {
                throw new IllegalStateException("클러스터링 일일 채점 중단: 판사 호출 실패 date=" + date + " 쌍 "
                        + pair.alertA() + " ↔ " + pair.alertB() + " (" + i + "/" + targets.size() + "쌍 판정 뒤)", e);
            }
            judged.add(new JudgedPair(pair, result));
            if ((i + 1) % PROGRESS_INTERVAL == 0) {
                log.info("클러스터링 일일 채점 진행: {} {}/{}쌍", date, i + 1, targets.size());
            }
        }
        return judged;
    }

    // 판사 판정을 라벨로 보고 PairCounter 로 센다(R5). UNSURE·형식 실패는 라벨이 아니라 "판정 없음"이라 PairCounter 에 넣지 않는다 -
    // 점수 분모에서 빠지는 것은 같지만 원인이 달라(판사가 모른다 / 응답 형식 오류) 개수는 toDayRow 가 각각 센다.
    // 대상 쌍은 SQL 이 두 알림 모두 배정된 것만 골랐으므로 assigned 는 항상 true 다.
    private static PairCountResult count(List<JudgedPair> judged) {
        List<Observation> observations = judged.stream()
                .filter(j -> j.verdict() == JudgeVerdict.SAME || j.verdict() == JudgeVerdict.DIFF)
                .map(j -> new Observation(j.pair().alertA(), j.pair().alertB(),
                        j.verdict() == JudgeVerdict.SAME ? Label.SAME : Label.DIFF, true, j.pair().systemSameEvent()))
                .toList();
        return PairCounter.count(observations);
    }

    // pair_count 는 UNSURE·형식 실패를 포함한 판정 쌍 수다. 토큰은 쌍마다의 응답을 합한 비용 확인용 값이다
    private static ClusteringDailyScore toDayRow(LocalDate date, int incidentCount, List<JudgedPair> judged,
                                                 PairCountResult counted, Agreement agreement) {
        int unsure = (int) judged.stream().filter(j -> j.verdict() == JudgeVerdict.UNSURE).count();
        int formatFailure = (int) judged.stream().filter(j -> j.verdict() == JudgeVerdict.FORMAT_FAILURE).count();
        long promptTokens = judged.stream().mapToLong(j -> j.result().promptTokens()).sum();
        long completionTokens = judged.stream().mapToLong(j -> j.result().completionTokens()).sum();
        return ClusteringDailyScore.of(date, incidentCount, judged.size(),
                counted.correctlyMerged(), counted.overMerged(), counted.missed(), unsure, formatFailure,
                ClusteringJudge.PROMPT_VERSION, agreement.sameNum(), agreement.sameDen(),
                agreement.diffNum(), agreement.diffDen(), promptTokens, completionTokens, LocalDateTime.now());
    }

    // 시스템 묶음 + 판사 DIFF = 과병합, 시스템 분리 + 판사 SAME = 과소병합. 그 밖(맞춘 쌍·UNSURE·형식 실패·제대로 나눔)은 null
    private static ClusteringDailyScorePair toRow(LocalDate date, JudgedPair j) {
        TargetPair p = j.pair();
        Disagreement disagreement = null;
        if (p.systemSameEvent() && j.verdict() == JudgeVerdict.DIFF) {
            disagreement = Disagreement.OVER_MERGED;
        } else if (!p.systemSameEvent() && j.verdict() == JudgeVerdict.SAME) {
            disagreement = Disagreement.MISSED;
        }
        return ClusteringDailyScorePair.of(date, p.alertA(), p.alertB(), j.verdict(), p.systemSameEvent(),
                p.eventA(), p.eventB(), disagreement);
    }

    // 날짜별 요약은 균형 정확도를 먼저 보인다. 두 비율은 분모가 0이어도 분자/분모를 남기며 저장 형식은 바꾸지 않는다.
    private static void logSummary(LocalDate date, ClusteringDailyScore d, PairCountResult counted) {
        // 1) 대표 지표도 PairCounter의 결과를 사용해 리포트·조회와 계산 정의가 갈라지지 않게 한다.
        String balancedAccuracy = counted.balancedAccuracy().isPresent()
                ? String.format(Locale.ROOT, "%.1f%%", counted.balancedAccuracy().getAsDouble()) : "-";
        String score = counted.score().isPresent() ? String.format(Locale.ROOT, "%.1f", counted.score().getAsDouble()) : "-";
        // 2) 기존 F1과 판사 일치율은 보조 근거로 남긴다. 모르겠음·형식 실패 개수는 저장된 일일 집계에서 읽는다.
        log.info("클러스터링 일일 채점 {}: 균형 정확도 {}, 빠짐없이 묶은 비율 {}/{}, 섞지 않은 비율 {}/{}, "
                        + "보조 맞게 묶은 비율 {}/{}, F1 {}, 판정 {}쌍(UNSURE {}, 형식 실패 {}), "
                        + "판사 일치율(버전 {}) 같은 사건 {}/{}·다른 사건 {}/{}",
                date, balancedAccuracy, counted.correctlyMerged(), counted.sameEventPairs(),
                counted.correctlySeparated(), counted.differentEventPairs(),
                counted.correctlyMerged(), counted.mergedPairs(), score,
                d.getPairCount(), d.getUnsureCount(), d.getFormatFailureCount(),
                d.getJudgeVersion(), d.getAgreementSameNum(), d.getAgreementSameDen(),
                d.getAgreementDiffNum(), d.getAgreementDiffDen());
    }
}
