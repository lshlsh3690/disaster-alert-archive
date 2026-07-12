package com.disaster.alert.alertapi.domain.disasteralert.tool;

import com.disaster.alert.alertapi.domain.disasteralert.service.DisasterAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 시도 개편 등으로 지역명 매칭이 실패해 법정동이 하나도 매핑되지 않은 채 저장된 disaster_alert를
 * 원본 지역명(originalRegion)으로 재처리하는 백필 도구.
 *
 * <p><b>활성 조건</b>: Spring Profile {@code region-backfill} 활성 시에만 실행.
 * 일반 dev / 운영 부팅에서는 빈 등록 자체가 안 됨.
 *
 * <p><b>배경</b>: 2026-07-01 전남·광주 통합특별시 개편 이후 공공데이터포털 API가 지역명 앞에
 * "전남광주통합특별시"를 붙이기 시작했는데, 개편된 법정동코드(마이그레이션 V108)가 반영되기 전에
 * 수집된 alert는 지역명이 legal_district 어디에도 매칭되지 않아 법정동 매핑 없이 저장되었다.
 * V108 적용 이후 신규 수집분은 정상 매핑되지만, 그 이전에 이미 저장된 데이터는 이 도구로
 * 원본 지역명(originalRegion)을 다시 매칭해 재처리해야 한다.
 *
 * <p><b>사용법</b>
 * <pre>
 * export SPRING_PROFILES_ACTIVE=region-backfill
 * cd backend && set -a && source ../.env.dev && set +a
 * ./gradlew bootRun
 * </pre>
 */
@Component
@Profile("region-backfill")
@RequiredArgsConstructor
@Slf4j
public class DisasterAlertRegionBackfillTool implements ApplicationRunner {

    private final DisasterAlertService disasterAlertService;
    private final JdbcTemplate jdbcTemplate;

    private static final int BATCH_SIZE = 200;

    @Override
    public void run(ApplicationArguments args) {
        List<Long> alertIds = fetchAlertIdsMissingRegion();
        log.info("DisasterAlertRegionBackfillTool 시작: 대상 {}건", alertIds.size());

        int updated = 0;
        for (int i = 0; i < alertIds.size(); i += BATCH_SIZE) {
            List<Long> batch = alertIds.subList(i, Math.min(i + BATCH_SIZE, alertIds.size()));
            try {
                updated += disasterAlertService.remapMissingRegions(batch);
            } catch (Exception e) {
                log.error("region-backfill: 배치 실패 (offset={})", i, e);
            }
            log.info("region-backfill 진행: {}/{} (누적 매핑 성공={})",
                    Math.min(i + BATCH_SIZE, alertIds.size()), alertIds.size(), updated);
        }

        log.info("DisasterAlertRegionBackfillTool 완료: 매핑 성공={}/{}", updated, alertIds.size());
    }

    /**
     * 법정동 매핑이 하나도 없고, 개편으로 이름이 바뀐 시도(전남광주통합특별시)를 원본 지역명에
     * 포함하는 alert만 대상으로 한다 — 매칭 실패 원인이 전혀 다른 데이터(예: 시도명 누락)까지
     * 잘못 건드리지 않기 위함.
     */
    private List<Long> fetchAlertIdsMissingRegion() {
        String sql = "SELECT disaster_alert_id FROM disaster_alert da "
                + "WHERE original_region LIKE '%전남광주통합특별시%' "
                + "AND NOT EXISTS (SELECT 1 FROM disaster_alert_region dar WHERE dar.disaster_alert_id = da.disaster_alert_id) "
                + "ORDER BY created_at ASC";
        return jdbcTemplate.queryForList(sql, Long.class);
    }
}
