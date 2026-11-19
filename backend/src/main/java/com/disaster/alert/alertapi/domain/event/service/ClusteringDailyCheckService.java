package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.event.dto.ClusteringDashboardDailyCheckAlertsResponse;
import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyCheck;
import com.disaster.alert.alertapi.domain.event.repository.ClusteringDailyCheckRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 하루 한 번, 지난 하루의 사건 알림 중 이벤트 미배정 건수를 세어 남기는 일일 구조 검사.
 *
 * <p>운영에 미배정 사건 알림이 551건 쌓여 있었는데 아무도 몰랐던 일을 막으려는 것이다. 스케줄러(또는 수동 실행)가
 * {@link #check(LocalDate)} 를 부르면 그 날짜의 숫자를 {@code clustering_daily_check} 에 한 줄로 남긴다.
 *
 * <p>운영 스케줄러는 {@link #checkRecent} 로 최근 며칠을 다시 세고, {@link #check} 는 그 하루 단위 계산이다.
 */
@Service
public class ClusteringDailyCheckService {

    // 숫자 쿼리 3개와 목록 쿼리가 공유하는 공통 조건. 한 곳에 둬야 화면의 숫자와 펼친 목록의 기준이 어긋나지 않는다.
    // 안내(ADVISORY)는 이벤트에 안 들어가는 게 정상이라 모두 INCIDENT 만 본다. 날짜는 [당일 00:00, 다음날 00:00) 반개구간이라
    // 자정 정각 알림이 이틀에 중복으로 잡히지 않는다(파라미터 2개: from, to).
    private static final String INCIDENT_IN_DAY = "created_at >= ? AND created_at < ? AND alert_kind = 'INCIDENT'";

    // 하루 한 번 도는 작업이라 쿼리를 합치지 않고 읽기 쉽게 나눈다.
    private static final String COUNT_INCIDENTS = "SELECT COUNT(*) FROM disaster_alert WHERE " + INCIDENT_IN_DAY;

    // 미배정 = 이벤트가 없는(event_id 가 비어 있는) 사건 알림
    private static final String COUNT_UNASSIGNED =
            "SELECT COUNT(*) FROM disaster_alert WHERE " + INCIDENT_IN_DAY + " AND event_id IS NULL";

    private static final String COUNT_NO_EMBEDDING =
            "SELECT COUNT(*) FROM disaster_alert WHERE " + INCIDENT_IN_DAY + " AND embedding IS NULL";

    // 펼친 목록 = 위 미배정 건수와 임베딩 없음 건수가 센 알림의 합집합. 정렬 끝에 id 를 둬 같은 시각 알림의 순서를 고정한다.
    private static final String SELECT_PROBLEM_ALERTS =
            "SELECT disaster_alert_id, created_at, disaster_type, message, "
                    + "(event_id IS NULL) AS unassigned, (embedding IS NULL) AS no_embedding, "
                    + "event_id, event_merge_method "
                    + "FROM disaster_alert WHERE " + INCIDENT_IN_DAY
                    + " AND (event_id IS NULL OR embedding IS NULL) "
                    + "ORDER BY created_at, disaster_alert_id";

    private final JdbcTemplate jdbcTemplate;
    private final ClusteringDailyCheckRepository repository;

    public ClusteringDailyCheckService(JdbcTemplate jdbcTemplate, ClusteringDailyCheckRepository repository) {
        this.jdbcTemplate = jdbcTemplate;
        this.repository = repository;
    }

    /**
     * 해당 날짜(created_at 이 그날 00:00 이상, 다음날 00:00 미만)에 생성된 INCIDENT 알림을 세어 저장한다.
     * 같은 날짜 줄이 이미 있으면 새 줄을 만들지 않고 건수를 덮어써 "하루 한 줄"을 유지한다.
     *
     * @return 저장된 검사 결과 (건수와 검사 시각)
     */
    @Transactional
    public ClusteringDailyCheck check(LocalDate date) {
        // 1) 날짜 범위는 [당일 00:00, 다음날 00:00) 반개구간 - 목록 조회와 같은 계산을 쓴다.
        LocalDateTime from = startOfDay(date);
        LocalDateTime to = startOfNextDay(date);

        // 2) 세 건수를 각각 센다
        int incident = jdbcTemplate.queryForObject(COUNT_INCIDENTS, Integer.class, from, to);
        int unassigned = jdbcTemplate.queryForObject(COUNT_UNASSIGNED, Integer.class, from, to);
        int noEmbedding = jdbcTemplate.queryForObject(COUNT_NO_EMBEDDING, Integer.class, from, to);

        // 3) 같은 날짜 줄이 있으면 엔티티 메서드로 덮어쓴다 - 재시도·수동 재실행이 중복 줄을 만들지 않는다.
        LocalDateTime checkedAt = LocalDateTime.now();
        ClusteringDailyCheck check = repository.findById(date).orElse(null);
        if (check == null) {
            check = ClusteringDailyCheck.of(date, incident, unassigned, noEmbedding, checkedAt);
        } else {
            check.update(incident, unassigned, noEmbedding, checkedAt);
        }
        return repository.save(check);
    }

    /**
     * 해당 날짜의 문제 알림(INCIDENT 이면서 이벤트 미배정이거나 임베딩 없음)을 발송 시각 오름차순으로 돌려준다.
     * 위 건수 쿼리와 같은 공통 조건·날짜 범위를 쓰므로 검사 당시와 같은 데이터라면 숫자와 목록이 일치한다.
     * 단 숫자는 검사 시점의 저장값이고 목록은 지금 조회한 값이라, 그 뒤 클러스터링·백필로 알림이 배정되면 목록이 더 짧을 수 있다.
     *
     * <p>둘 다 해당하는 알림은 한 번만 나오고 두 플래그가 모두 true 다. 해당 알림이 없으면 빈 목록이다.
     */
    @Transactional(readOnly = true)
    public ClusteringDashboardDailyCheckAlertsResponse problemAlerts(LocalDate date) {
        // 1) 날짜 범위는 건수 쿼리와 같은 헬퍼로 계산한다(맨 아래 파라미터).
        // 2) 플래그는 SQL 의 IS NULL 판정 그대로 읽는다. 미배정이면 event_id·merge_method 도 null 로 와 그대로 담는다.
        List<ClusteringDashboardDailyCheckAlertsResponse.Alert> alerts = jdbcTemplate.query(
                SELECT_PROBLEM_ALERTS,
                (rs, rowNum) -> new ClusteringDashboardDailyCheckAlertsResponse.Alert(
                        rs.getLong("disaster_alert_id"),
                        rs.getObject("created_at", LocalDateTime.class),
                        rs.getString("disaster_type"),
                        rs.getString("message"),
                        rs.getBoolean("unassigned"),
                        rs.getBoolean("no_embedding"),
                        // event_id 는 NULL 가능 - getLong 은 NULL 을 0 으로 바꾸므로 getObject 로 읽는다
                        rs.getObject("event_id", Long.class),
                        rs.getString("event_merge_method")),
                startOfDay(date), startOfNextDay(date));
        return new ClusteringDashboardDailyCheckAlertsResponse(date, alerts);
    }

    private static LocalDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay();
    }

    private static LocalDateTime startOfNextDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay();
    }

    /**
     * lastDate 를 포함해 과거로 days 일을 각각 다시 검사해 저장하고, 미배정이 새로 잡혔거나 이전 저장값보다
     * 늘어난 날의 결과만 돌려준다.
     *
     * <p>매일 최근 N일을 통째로 다시 세는 이유는 앱이 꺼져 검사 행이 빠진 날을 다음 실행에서 채우고, 늦게 수집된
     * 알림으로 달라진 숫자를 반영하기 위해서다. 반환값을 "새로 잡히거나 늘어난 날"로 좁혀 같은 미배정에 경보가
     * 매일 반복되지 않게 한다. 결과는 오래된 날짜부터 순서대로 담긴다.
     *
     * <p>{@link #check} 는 같은 클래스 안의 호출이라 프록시를 다시 타지 않고 이 메서드의 트랜잭션에 그대로
     * 합류한다. 즉 N일이 한 트랜잭션이고, 중간에 실패하면 전체가 롤백된다(하루 1회 작업이라 허용).
     */
    @Transactional
    public List<ClusteringDailyCheck> checkRecent(LocalDate lastDate, int days) {
        List<ClusteringDailyCheck> flagged = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = lastDate.minusDays(i);

            // 1) 덮어쓰기 전에 이전 저장값을 먼저 읽는다 - 같은 영속성 컨텍스트에서는 check 가 돌려주는 엔티티와
            //    여기서 읽은 엔티티가 같은 인스턴스라, update 뒤에 읽으면 이미 새 값이라 비교가 항상 "같음"이 된다.
            Integer previousUnassigned = repository.findById(date)
                    .map(ClusteringDailyCheck::getUnassignedCount)
                    .orElse(null);

            // 2) 집계·저장은 기존 check 를 그대로 쓴다 - 하루 한 줄 덮어쓰기 규칙이 한 곳에만 있게 한다.
            ClusteringDailyCheck current = check(date);

            // 3) 미배정이 있고, 저장 행이 없었거나(새로 잡힘) 이전보다 늘었을 때만 알린다. 같은 값·감소·0건은 제외.
            int unassigned = current.getUnassignedCount();
            if (unassigned > 0 && (previousUnassigned == null || unassigned > previousUnassigned)) {
                flagged.add(current);
            }
        }
        return flagged;
    }
}
