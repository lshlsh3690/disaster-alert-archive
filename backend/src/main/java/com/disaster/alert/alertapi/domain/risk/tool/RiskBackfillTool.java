package com.disaster.alert.alertapi.domain.risk.tool;

import com.disaster.alert.alertapi.domain.risk.service.RiskCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 과거 disaster_events 전체에 위험도 백필.
 *
 * <p>전제: 팀원 EventClusteringBackfillTool 로 이벤트/임베딩 백필이 끝난 후 실행.
 *
 * <pre>
 * export SPRING_PROFILES_ACTIVE=risk-backfill
 * ./gradlew bootRun
 * </pre>
 *
 * <p>처리 후 region_risk_index 는 다음 스케줄러 주기에 자동 갱신되거나,
 * 필요 시 전체 시군구 강제 재계산 1회 추가.
 */
@Component
@Profile("risk-backfill")
@RequiredArgsConstructor
@Slf4j
public class RiskBackfillTool implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final RiskCalculationService riskService;

    @Override
    public void run(ApplicationArguments args) {
        List<Long> eventIds = jdbc.queryForList(
                "SELECT id FROM disaster_events ORDER BY created_at ASC", Long.class);
        log.info("RiskBackfillTool 시작: 이벤트 {}건", eventIds.size());

        int processed = 0, errors = 0;
        long t0 = System.currentTimeMillis();

        for (Long id : eventIds) {
            try {
                riskService.recomputeEventRisk(id);  // impact 기록
                processed++;
            } catch (Exception e) {
                errors++;
                log.error("backfill 실패 eventId={}", id, e);
            }
            if (processed % 500 == 0) {
                log.info("backfill 진행 {}/{}", processed, eventIds.size());
            }
        }

        // 전체 시군구 1회 강제 재계산 (region_risk_index 즉시 채우기)
        List<String> sigungus = jdbc.queryForList(
                "SELECT DISTINCT LEFT(region_code, 5) FROM event_region_impact", String.class);
        sigungus.forEach(code -> {
            try {
                riskService.recomputeRegionRisk(code);
            } catch (Exception e) {
                log.error("backfill 지역 재계산 실패 region={}", code, e);
            }
        });

        long elapsed = (System.currentTimeMillis() - t0) / 1000;
        log.info("RiskBackfillTool 완료: 처리={}, 오류={}, 시군구={}, 소요={}s",
                processed, errors, sigungus.size(), elapsed);
    }
}
