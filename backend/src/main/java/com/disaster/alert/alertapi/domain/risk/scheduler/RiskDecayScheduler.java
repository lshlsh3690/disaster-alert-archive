package com.disaster.alert.alertapi.domain.risk.scheduler;

import com.disaster.alert.alertapi.domain.risk.RiskConstants;
import com.disaster.alert.alertapi.domain.risk.model.RegionRiskHistory;
import com.disaster.alert.alertapi.domain.risk.model.RegionRiskIndex;
import com.disaster.alert.alertapi.domain.risk.repository.EventRegionImpactRepository;
import com.disaster.alert.alertapi.domain.risk.repository.RegionRiskHistoryRepository;
import com.disaster.alert.alertapi.domain.risk.repository.RegionRiskIndexRepository;
import com.disaster.alert.alertapi.domain.risk.service.RiskCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 위험도 시간 감쇠 / 시계열 스냅샷 / retention 스케줄러.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RiskDecayScheduler {

    private final EventRegionImpactRepository impactRepo;
    private final RegionRiskIndexRepository indexRepo;
    private final RegionRiskHistoryRepository historyRepo;
    private final RiskCalculationService riskService;

    /**
     * 3분마다 활성 시군구 재계산(시간 감쇠 반영).
     *
     * <p>C4: 재계산 대상 = (active impact 있는 시군구) ∪ (현재 score>0 인 시군구).
     * 두 번째 집합이 있어야 impact 만료된 지역이 0 으로 내려간다.
     */
    @Scheduled(cron = "0 */3 * * * *")
    public void recomputeActiveRegions() {
        LocalDateTime since = LocalDateTime.now().minusDays(RiskConstants.ACTIVE_WINDOW_DAYS);

        Set<String> targets = new HashSet<>(impactRepo.findActiveSigunguCodes(since));
        targets.addAll(indexRepo.findNonZeroRegionCodes());

        if (targets.isEmpty()) return;

        // 각 재계산이 @Transactional 이라 DB 커넥션을 점유한다. parallelStream(공용 ForkJoinPool)은
        // Hikari pool(10) 을 넘는 동시 커넥션 경합 위험이 있어 순차 처리 — 3분 주기엔 충분히 빠름.
        targets.forEach(code -> {
            try {
                riskService.recomputeRegionRisk(code);
            } catch (Exception e) {
                log.error("지역 위험도 재계산 실패 region={}", code, e);
            }
        });
        log.debug("recomputeActiveRegions: {}개 시군구 재계산", targets.size());
    }

    /** 1시간마다 nonzero 지역 시계열 스냅샷 (Phase 2 Chronos 입력 축적). */
    @Scheduled(cron = "0 0 * * * *")
    public void snapshotHistory() {
        LocalDateTime now = LocalDateTime.now();
        List<RegionRiskIndex> nonZero = indexRepo.findAllNonZero();
        nonZero.forEach(r ->
                historyRepo.save(RegionRiskHistory.of(r.getRegionCode(), now, r.getRiskScore())));
        log.debug("snapshotHistory: {}개 시군구 스냅샷", nonZero.size());
    }

    /** 매일 새벽 retention 정리. */
    // @Modifying 쿼리(deleteOlderThan)를 실행하므로 트랜잭션이 필수적임.
    // 누락 시 스케줄러 실행 중 TransactionRequiredException 발생.
    @Transactional
    @Scheduled(cron = "0 30 4 * * *")
    public void cleanupHistory() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RiskConstants.HISTORY_RETENTION_DAYS);
        int deleted = historyRepo.deleteOlderThan(cutoff);
        if (deleted > 0) log.info("region_risk_history {}건 정리 (cutoff={})", deleted, cutoff);
    }
}
