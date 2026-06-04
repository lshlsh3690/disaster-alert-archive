package com.disaster.alert.alertapi.domain.risk.scheduler;

import com.disaster.alert.alertapi.domain.risk.service.RiskMaintenanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 위험도 시간 감쇠 / 시계열 스냅샷 / retention 스케줄러.
 * <p>본 클래스는 인프라/표현 계층으로서 트리거 역할만 수행하며, 실제 로직은 RiskMaintenanceService 에 위임합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RiskDecayScheduler {

    private final RiskMaintenanceService maintenanceService;

    /** 3분마다 활성 시군구 재계산(시간 감쇠 반영) */
    @Scheduled(cron = "0 */3 * * * *")
    public void recomputeActiveRegions() {
        maintenanceService.recomputeActiveRegions();
    }

    /** 1시간마다 nonzero 지역 시계열 스냅샷 (Phase 2 Chronos 입력 축적) */
    @Scheduled(cron = "0 0 * * * *")
    public void snapshotHistory() {
        maintenanceService.snapshotHistory();
    }

    /** 매일 밤 11시 50분, 오늘 치 데이터 일별 요약 (Rollup) */
    @Scheduled(cron = "0 50 23 * * *")
    public void rollupDaily() {
        maintenanceService.rollupDaily();
    }

    /** 매일 새벽 retention 정리 */
    @Scheduled(cron = "0 30 4 * * *")
    public void cleanupHistory() {
        maintenanceService.cleanupHistory();
    }
}
