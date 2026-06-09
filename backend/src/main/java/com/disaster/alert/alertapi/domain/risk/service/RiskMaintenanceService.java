package com.disaster.alert.alertapi.domain.risk.service;

import com.disaster.alert.alertapi.domain.risk.RiskConstants;
import com.disaster.alert.alertapi.domain.risk.model.RegionRiskHistory;
import com.disaster.alert.alertapi.domain.risk.model.RegionRiskIndex;
import com.disaster.alert.alertapi.domain.risk.repository.EventRegionImpactRepository;
import com.disaster.alert.alertapi.domain.risk.repository.RegionRiskDailyRepository;
import com.disaster.alert.alertapi.domain.risk.repository.RegionRiskHistoryRepository;
import com.disaster.alert.alertapi.domain.risk.repository.RegionRiskIndexRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 위험도 유지보수 서비스 (스냅샷, 요약, 정리, 일괄 계산).
 * <p>스케줄러 등의 인프라 계층에서 호출되는 순수 비즈니스 서비스입니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RiskMaintenanceService {

    private final EventRegionImpactRepository impactRepo;
    private final RegionRiskIndexRepository indexRepo;
    private final RegionRiskHistoryRepository historyRepo;
    private final RegionRiskDailyRepository dailyRepo;
    private final RiskCalculationService riskService;

    /** 활성 시군구 재계산(시간 감쇠 반영) */
    public void recomputeActiveRegions() {
        LocalDateTime since = LocalDateTime.now().minusDays(RiskConstants.ACTIVE_WINDOW_DAYS);

        Set<String> targets = new HashSet<>(impactRepo.findActiveSigunguCodes(since));
        targets.addAll(indexRepo.findNonZeroRegionCodes());

        if (targets.isEmpty()) return;

        targets.forEach(code -> {
            try {
                riskService.recomputeRegionRisk(code);
            } catch (Exception e) {
                log.error("지역 위험도 재계산 실패 region={}", code, e);
            }
        });
        log.debug("recomputeActiveRegions: {}개 시군구 재계산", targets.size());
    }

    /** 시계열 스냅샷 (Phase 2 Chronos 입력 축적) */
    @Transactional
    public void snapshotHistory() {
        LocalDateTime now = LocalDateTime.now();
        List<RegionRiskIndex> nonZero = indexRepo.findAllNonZero();
        nonZero.forEach(r ->
                historyRepo.save(RegionRiskHistory.of(r.getRegionCode(), now, r.getRiskScore())));
        log.debug("snapshotHistory: {}개 시군구 스냅샷", nonZero.size());
    }

    /** 매일 밤 요약 (Rollup) */
    @Transactional
    public void rollupDaily() {
        LocalDateTime startOfToday = java.time.LocalDate.now().atStartOfDay();
        LocalDateTime startOfTomorrow = startOfToday.plusDays(1);
        
        dailyRepo.aggregateDaily(startOfToday, startOfTomorrow);
        log.info("오늘({}) 위험도 일별 요약(Rollup) 완료", startOfToday.toLocalDate());
    }

    /** 오래된 원본 데이터 정리 (Cleanup) */
    @Transactional
    public void cleanupHistory() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RiskConstants.HISTORY_RETENTION_DAYS);
        int deleted = historyRepo.deleteOlderThan(cutoff);
        if (deleted > 0) log.info("region_risk_history {}건 정리 (cutoff={})", deleted, cutoff);
    }
}
