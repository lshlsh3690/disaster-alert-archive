package com.disaster.alert.alertapi.domain.event.tool;

import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.event.service.EventClusteringService;
import com.disaster.alert.alertapi.domain.event.service.EventCrossRegionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 과거 disaster_alert 전체 임베딩 + 클러스터링 백필 도구.
 *
 * <p><b>활성 조건</b>: Spring Profile {@code backfill} 활성 시에만 실행.
 * 일반 dev / 운영 부팅에서는 빈 등록 자체가 안 됨.
 *
 * <p><b>3가지 모드</b>
 * <ul>
 *   <li>기본: {@code embedding IS NULL} 알림을 1건씩 임베딩+클러스터링 (순차, 느림)</li>
 *   <li>{@code --backfill.embed-only=true}: 임베딩만 <b>배치</b>로 생성·저장 (빠름, 클러스터링 X)</li>
 *   <li>{@code --backfill.recluster=true}: 저장된 임베딩으로 재클러스터링 (OpenAI 호출 X, 튜닝용)</li>
 * </ul>
 *
 * <p><b>권장 절차 (전체 백필/검증)</b>
 * <pre>
 * export CLUSTERING_ENABLED=true SPRING_PROFILES_ACTIVE=backfill
 * cd backend && set -a && source ../.env.dev && set +a
 * # 1) 배치 임베딩 (전체, ~분 단위)
 * ./gradlew bootRun --args='--backfill.embed-only=true'
 * # 2) 이벤트 비우고
 * #    psql -c "TRUNCATE event_alert_mapping, disaster_events RESTART IDENTITY;"
 * # 3) 저장 벡터로 클러스터링 (OpenAI 호출 X, ~분 단위)
 * ./gradlew bootRun --args='--backfill.recluster=true'
 * # 4) 임계값 튜닝: application.yml 수정 → (2)~(3) 반복 (공짜)
 * </pre>
 *
 * <p><b>처리 흐름</b>: disaster_alert 를 created_at ASC 로 정렬해 클러스터링.
 * 알림이 created_at 순서대로 처리되어야 시계열 누적 사건 흐름을 정확히 재현.
 */
@Component
@Profile("backfill")
@RequiredArgsConstructor
@Slf4j
public class EventClusteringBackfillTool implements ApplicationRunner {

    private final DisasterAlertRepository disasterAlertRepository;
    private final EventClusteringService eventClusteringService;
    private final EventCrossRegionService eventCrossRegionService;
    private final JdbcTemplate jdbcTemplate;

    /** 임베딩 배치 호출 크기 — OpenAI 한 번에 보낼 입력 수. */
    private static final int EMBED_BATCH_SIZE = 200;

    @Override
    public void run(ApplicationArguments args) {
        Integer limit = parseLimit(args);
        boolean recluster = parseRecluster(args);
        boolean embedOnly = parseFlag(args, "backfill.embed-only");
        boolean crossRegion = parseFlag(args, "backfill.cross-region");

        if (embedOnly) {
            runEmbedOnly(limit);
            return;
        }
        if (crossRegion) {
            runCrossRegion(limit);
            return;
        }

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
        return parseFlag(args, "backfill.recluster");
    }

    private boolean parseFlag(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        return values != null && !values.isEmpty() && Boolean.parseBoolean(values.get(0));
    }

    /**
     * 임베딩만 배치로 생성·저장 (클러스터링 X). embedding IS NULL 대상.
     * 200건씩 묶어 OpenAI 호출 → 43k건도 분 단위.
     */
    private void runEmbedOnly(Integer limit) {
        String sql = "SELECT disaster_alert_id, message FROM disaster_alert "
                + "WHERE embedding IS NULL AND message IS NOT NULL AND message <> '' "
                + "ORDER BY created_at ASC"
                + (limit == null ? "" : " LIMIT " + limit);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        log.info("embed-only 시작: 대상 {}건 (배치 {}건씩)", rows.size(), EMBED_BATCH_SIZE);
        int done = 0;
        int errors = 0;
        long t0 = System.currentTimeMillis();

        for (int i = 0; i < rows.size(); i += EMBED_BATCH_SIZE) {
            List<Map<String, Object>> batch = rows.subList(i, Math.min(i + EMBED_BATCH_SIZE, rows.size()));
            List<Long> ids = batch.stream().map(r -> ((Number) r.get("disaster_alert_id")).longValue()).toList();
            List<String> msgs = batch.stream().map(r -> (String) r.get("message")).toList();
            try {
                eventClusteringService.storeEmbeddings(ids, msgs);
                done += ids.size();
            } catch (Exception e) {
                errors += ids.size();
                log.error("embed-only: 배치 실패 (offset {})", i, e);
            }
            long elapsed = System.currentTimeMillis() - t0;
            log.info("embed-only 진행: {}/{} (errors={}, elapsed={}s)", done, rows.size(), errors, elapsed / 1000);
        }
        log.info("embed-only 완료: 저장={}, 오류={}, 소요={}s", done, errors, (System.currentTimeMillis() - t0) / 1000);
    }

    /**
     * cross-region LLM 병합 백필 (recluster <b>후</b> 실행). 임베딩된 '기타' 알림을 created_at ASC 로
     * 순회하며 {@link EventCrossRegionService#linkCrossRegion} 호출 — mover 게이트·LLM 판정은 내부에서.
     *
     * <p>{@code clustering.cross-region.enabled=true} 필요(아니면 전건 no-op). recluster 처럼
     * 단일 프로세스로 실행. 비용은 mover 후보가 있는 건만 LLM(gpt-4o-mini) 호출.
     */
    private void runCrossRegion(Integer limit) {
        String sql = "SELECT disaster_alert_id FROM disaster_alert "
                + "WHERE embedding IS NOT NULL AND disaster_type = '기타' "
                + "ORDER BY created_at ASC"
                + (limit == null ? "" : " LIMIT " + limit);
        List<Long> ids = jdbcTemplate.queryForList(sql, Long.class);

        log.info("cross-region 백필 시작: 기타 임베딩 대상 {}건 (mover 게이트·LLM 은 서비스 내부)", ids.size());
        int processed = 0;
        int errors = 0;
        long t0 = System.currentTimeMillis();
        for (Long alertId : ids) {
            try {
                eventCrossRegionService.linkCrossRegion(alertId);
                processed++;
            } catch (Exception e) {
                errors++;
                log.error("cross-region: alertId={} 처리 중 오류", alertId, e);
            }
            if (processed % 100 == 0 && processed > 0) {
                long elapsed = System.currentTimeMillis() - t0;
                log.info("cross-region 진행: {}/{} (errors={}, elapsed={}s)",
                        processed, ids.size(), errors, elapsed / 1000);
            }
        }
        log.info("cross-region 백필 완료: 처리={}, 오류={}, 소요={}s",
                processed, errors, (System.currentTimeMillis() - t0) / 1000);
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
