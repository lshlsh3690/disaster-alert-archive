package com.disaster.alert.alertapi.domain.event.tool;

import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.event.service.EventClusteringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 과거 disaster_alert 전체 임베딩 + 클러스터링 백필 도구.
 *
 * <p><b>활성 조건</b>: Spring Profile {@code backfill} 활성 시에만 실행.
 * 일반 dev / 운영 부팅에서는 빈 등록 자체가 안 됨.
 *
 * <p><b>실행 절차 (Phase B)</b>
 * <pre>
 * # 1. .env.dev 에 OPENAI_API_KEY 추가
 * # 2. clustering.enabled 활성화 + backfill profile 활성화
 * export CLUSTERING_ENABLED=true
 * export SPRING_PROFILES_ACTIVE=backfill
 * # 3. 샘플 검증 (limit 500)
 * cd backend
 * set -a && source ../.env.dev && set +a
 * ./gradlew bootRun --args='--backfill.limit=500'
 * </pre>
 *
 * <p><b>처리 흐름</b>: disaster_alert 를 created_at ASC 로 정렬 → 한 건씩
 * {@link EventClusteringService#clusterNewAlert(Long)} 호출.
 * 알림이 created_at 순서대로 처리되어야 클러스터링이 시계열 누적 사건 흐름을 정확히 재현.
 *
 * <p><b>주의</b>: 백필 종료 후 별도 도구로 disaster_alert.embedding, disaster_events,
 * event_alert_mapping 을 V31_xx / V32 / V33 시드 SQL 로 덤프 (이번 PR 범위 외).
 */
@Component
@Profile("backfill")
@RequiredArgsConstructor
@Slf4j
public class EventClusteringBackfillTool implements ApplicationRunner {

    private final DisasterAlertRepository disasterAlertRepository;
    private final EventClusteringService eventClusteringService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Integer limit = parseLimit(args);
        boolean recluster = parseRecluster(args);

        List<Long> alertIds = fetchAlertIds(limit, recluster);

        log.info("EventClusteringBackfillTool 시작: 대상 {}건 (limit={}, recluster={})",
                alertIds.size(), limit, recluster);
        if (recluster) {
            log.info("  recluster 모드: 저장된 임베딩 재사용(OpenAI 호출 X). " +
                    "이벤트 테이블을 먼저 비웠는지 확인하세요 (TRUNCATE event_alert_mapping, disaster_events).");
        }

        int processed = 0;
        int errors = 0;
        long t0 = System.currentTimeMillis();
        for (Long alertId : alertIds) {
            try {
                eventClusteringService.clusterNewAlert(alertId);
                processed++;
            } catch (Exception e) {
                errors++;
                log.error("backfill: alertId={} 처리 중 오류", alertId, e);
            }
            if (processed % 100 == 0 && processed > 0) {
                long elapsed = System.currentTimeMillis() - t0;
                log.info("backfill 진행: {}/{} (errors={}, elapsed={}s)",
                        processed, alertIds.size(), errors, elapsed / 1000);
            }
        }

        long elapsed = System.currentTimeMillis() - t0;
        log.info("EventClusteringBackfillTool 완료: 처리={}, 오류={}, 소요={}s",
                processed, errors, elapsed / 1000);
    }

    private Integer parseLimit(ApplicationArguments args) {
        List<String> values = args.getOptionValues("backfill.limit");
        if (values == null || values.isEmpty()) return null;
        try {
            return Integer.parseInt(values.get(0));
        } catch (NumberFormatException e) {
            log.warn("backfill.limit 파싱 실패 (값={}). 전체 백필로 진행.", values.get(0));
            return null;
        }
    }

    /** {@code --backfill.recluster=true} 면 저장된 임베딩으로 재클러스터링(임계값 튜닝용). */
    private boolean parseRecluster(ApplicationArguments args) {
        List<String> values = args.getOptionValues("backfill.recluster");
        return values != null && !values.isEmpty() && Boolean.parseBoolean(values.get(0));
    }

    /**
     * created_at ASC 정렬 대상 ID 목록.
     *
     * <p>recluster=false (기본): {@code embedding IS NULL} 만 — 임베딩 미생성 알림만 처리(재실행 안전).
     * <p>recluster=true: {@code embedding IS NOT NULL} 전체 — 이미 임베딩된 알림을 다시 클러스터링
     * (튜닝 시 이벤트 비우고 저장 벡터로 재구성).
     */
    private List<Long> fetchAlertIds(Integer limit, boolean recluster) {
        String where = recluster ? "embedding IS NOT NULL" : "embedding IS NULL";
        String sql = "SELECT disaster_alert_id FROM disaster_alert WHERE " + where
                + " ORDER BY created_at ASC"
                + (limit == null ? "" : " LIMIT " + limit);
        return jdbcTemplate.queryForList(sql, Long.class);
    }
}
