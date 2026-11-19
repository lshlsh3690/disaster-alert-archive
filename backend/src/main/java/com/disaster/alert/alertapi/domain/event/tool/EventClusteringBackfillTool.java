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

import java.time.LocalDate;
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
 *   <li>{@code --backfill.recluster=true}: 저장된 임베딩으로 재클러스터링 (임베딩 호출 X, 튜닝용).
 *       단 사고성 유형의 borderline 후보는 LLM 폴백(gpt-4o-mini)을 호출하므로 완전히 공짜는 아니다</li>
 *   <li>{@code --backfill.unassigned-since=2026-11-18}: 그 날짜(포함) 이후의 <b>미배정</b>(event_id IS NULL) 알림만
 *       지금 기준으로 다시 클러스터링. 이미 묶인 알림·이벤트는 건드리지 않아 이벤트 id 가 유지된다. 클러스터링 기준을
 *       바꾼 뒤 최근 알림에만 새 기준을 적용할 때 쓴다(2026-11-19 전국 예정 행사 도입 때 수능 문자 연결용으로 추가).
 *       이벤트를 비울 필요가 없다. 기준 변경이 이미 묶인 알림의 결과도 바꾸는 경우엔 이 모드로는 부족하다</li>
 * </ul>
 *
 * <p><b>권장 절차 (전체 백필/검증)</b>
 * <pre>
 * export SPRING_PROFILES_ACTIVE=backfill
 * cd backend && set -a && source ../.env.dev && set +a
 * # 1) 배치 임베딩 (전체, ~분 단위)
 * ./gradlew bootRun --args='--backfill.embed-only=true'
 * # 2) 이벤트 비우기 — 알림의 이벤트 배정(event_ 컬럼 5개)을 먼저 지우고, 이벤트를 참조하는 번역·위험도 표를 비운 뒤
 * #    이벤트를 DELETE 한다. disaster_alert.event_id 가 disaster_events 를 참조해 disaster_events 는 TRUNCATE 할 수 없다
 * #    (TRUNCATE ... CASCADE 는 disaster_alert 까지 비우므로 절대 쓰지 않는다).
 * #    psql -c "UPDATE disaster_alert SET event_id = NULL, event_sequence_no = NULL, event_similarity_score = NULL, event_merge_method = NULL, event_added_at = NULL WHERE event_id IS NOT NULL OR event_sequence_no IS NOT NULL;"
 * #    psql -c "TRUNCATE disaster_event_translation, event_region_impact, region_risk_index;"
 * #    psql -c "DELETE FROM disaster_events; SELECT setval(pg_get_serial_sequence('disaster_events', 'id'), 1, false);"
 * # 3) 저장 벡터로 클러스터링 (임베딩 호출 X, borderline 사고성 건만 LLM 호출, ~분 단위)
 * ./gradlew bootRun --args='--backfill.recluster=true'
 * # 4) 임계값 튜닝: application.yml 수정 → (2)~(3) 반복 (임베딩 비용 없음, LLM 폴백 비용만)
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
    private final JdbcTemplate jdbcTemplate;

    /** 임베딩 배치 호출 크기 — OpenAI 한 번에 보낼 입력 수. */
    private static final int EMBED_BATCH_SIZE = 200;

    @Override
    public void run(ApplicationArguments args) {
        Integer limit = parseLimit(args);
        boolean recluster = parseRecluster(args);
        boolean embedOnly = parseFlag(args, "backfill.embed-only");

        if (embedOnly) {
            runEmbedOnly(limit);
            return;
        }

        LocalDate unassignedSince = parseDate(args, "backfill.unassigned-since");
        List<Long> alertIds = unassignedSince != null
                ? fetchUnassignedSince(unassignedSince)
                : fetchAlertIds(limit, recluster);

        log.info("EventClusteringBackfillTool 시작: 대상 {}건 (limit={}, recluster={}, unassignedSince={})",
                alertIds.size(), limit, recluster, unassignedSince);
        if (recluster && unassignedSince == null) {
            log.info("  recluster 모드: 저장된 임베딩 재사용(임베딩 호출 X, borderline 사고성 건은 LLM 폴백 호출). " +
                    "이벤트를 먼저 비웠는지 확인하세요 (disaster_alert 의 event_ 컬럼 NULL → "
                    + "TRUNCATE disaster_event_translation, event_region_impact, region_risk_index → DELETE FROM disaster_events).");
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
     * {@code --name=yyyy-MM-dd} 를 읽는다. 옵션이 없으면 null. 값 없이 {@code --name} 만 주면 빈 목록이 오는데, 이걸 "옵션
     * 없음"으로 보면 날짜 제한 없는 기본 모드(임베딩 없는 알림 전체를 임베딩·클러스터링)로 넘어가므로 바로 실패시킨다
     * (CodeRabbit PR #347 지적). 형식이 틀려도 엉뚱한 범위를 돌지 않게 실패한다.
     */
    private LocalDate parseDate(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        if (values == null) {
            return null;
        }
        if (values.isEmpty() || values.get(0).isBlank()) {
            throw new IllegalArgumentException("--" + name + " 에 날짜(yyyy-MM-dd)가 없습니다");
        }
        return LocalDate.parse(values.get(0));
    }

    /** 그 날짜(포함) 이후의 미배정 알림 — 처리 순서는 {@link #fetchAlertIds} 와 같다(created_at, id 오름차순). */
    private List<Long> fetchUnassignedSince(LocalDate since) {
        String sql = "SELECT disaster_alert_id FROM disaster_alert WHERE event_id IS NULL AND created_at >= ?"
                + " ORDER BY created_at ASC, disaster_alert_id ASC";
        return jdbcTemplate.queryForList(sql, Long.class, since.atStartOfDay());
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
     * created_at ASC 정렬 대상 ID 목록.
     *
     * <p>recluster=false (기본): {@code embedding IS NULL} 만 — 임베딩 미생성 알림만 처리(재실행 안전).
     * <p>recluster=true: {@code embedding IS NOT NULL} 전체 — 이미 임베딩된 알림을 다시 클러스터링
     * (튜닝 시 이벤트 비우고 저장 벡터로 재구성).
     */
    private List<Long> fetchAlertIds(Integer limit, boolean recluster) {
        String where = recluster ? "embedding IS NOT NULL" : "embedding IS NULL";
        // created_at 이 같은 알림은 id 오름차순으로 처리한다 — 처리 순서가 실행마다 달라지면 같은 데이터로도 클러스터링 결과가 달라진다
        String sql = "SELECT disaster_alert_id FROM disaster_alert WHERE " + where
                + " ORDER BY created_at ASC, disaster_alert_id ASC"
                + (limit == null ? "" : " LIMIT " + limit);
        return jdbcTemplate.queryForList(sql, Long.class);
    }
}
