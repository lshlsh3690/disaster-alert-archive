package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyCheck;
import com.disaster.alert.alertapi.domain.event.repository.ClusteringDailyCheckRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 하루 한 번, 지난 하루의 사건 알림 중 이벤트 미배정 건수를 세어 남기는 일일 구조 검사.
 *
 * <p>운영에 미배정 사건 알림이 551건 쌓여 있었는데 아무도 몰랐던 일을 막으려는 것이다. 스케줄러(또는 수동 실행)가
 * {@link #check(LocalDate)} 를 부르면 그 날짜의 숫자를 {@code clustering_daily_check} 에 한 줄로 남긴다.
 */
@Service
public class ClusteringDailyCheckService {

    // 세 건수를 한 번의 스캔으로 구한다. 건수만 필요하므로 임베딩 벡터나 알림 행은 앱으로 가져오지 않는다.
    // 안내(ADVISORY)는 이벤트에 안 들어가는 게 정상이라 INCIDENT 만 센다.
    // 미배정은 LEFT JOIN 이 아니라 NOT EXISTS 로 판정한다 - 한 알림이 매핑 행을 여러 개 가져도 건수가 부풀지 않는다.
    private static final String COUNT_SQL = """
            SELECT COUNT(*) AS incident_count,
                   COUNT(*) FILTER (WHERE NOT EXISTS (
                       SELECT 1 FROM event_alert_mapping m WHERE m.alert_id = a.disaster_alert_id)) AS unassigned_count,
                   COUNT(*) FILTER (WHERE a.embedding IS NULL) AS no_embedding_count
            FROM disaster_alert a
            WHERE a.created_at >= ? AND a.created_at < ?
              AND a.alert_kind = 'INCIDENT'
            """;

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
        // 1) 날짜 범위는 [당일 00:00, 다음날 00:00) 반개구간 - 자정 정각 알림이 이틀에 중복으로 잡히지 않는다.
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();

        // 2) 집계 한 줄을 읽는다. 집계 쿼리는 항상 한 행을 돌려주므로(대상이 없으면 0, 0, 0) null 분기가 필요 없다.
        int[] counts = jdbcTemplate.queryForObject(COUNT_SQL,
                (rs, rowNum) -> new int[]{
                        rs.getInt("incident_count"),
                        rs.getInt("unassigned_count"),
                        rs.getInt("no_embedding_count")},
                from, to);

        // 3) 같은 날짜 줄이 있으면 엔티티 메서드로 덮어쓴다 - 재시도·수동 재실행이 중복 줄을 만들지 않는다.
        LocalDateTime checkedAt = LocalDateTime.now();
        ClusteringDailyCheck check = repository.findById(date).orElse(null);
        if (check == null) {
            check = ClusteringDailyCheck.of(date, counts[0], counts[1], counts[2], checkedAt);
        } else {
            check.update(counts[0], counts[1], counts[2], checkedAt);
        }
        return repository.save(check);
    }

    // Red 단계 뼈대 - 구현은 Green 에서
    /**
     * lastDate 를 포함해 과거로 days 일을 각각 다시 검사해 저장하고, 미배정이 새로 잡혔거나 이전 저장값보다
     * 늘어난 날의 결과만 돌려준다.
     */
    @Transactional
    public List<ClusteringDailyCheck> checkRecent(LocalDate lastDate, int days) {
        return List.of();
    }
}
