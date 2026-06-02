package com.disaster.alert.alertapi.domain.risk.service;

import com.disaster.alert.alertapi.domain.risk.RiskConstants;
import com.disaster.alert.alertapi.domain.risk.controller.dto.RiskResponses.ContributingEvent;
import com.disaster.alert.alertapi.domain.risk.controller.dto.RiskResponses.RegionRiskDetail;
import com.disaster.alert.alertapi.domain.risk.controller.dto.RiskResponses.RegionRiskMapItem;
import com.disaster.alert.alertapi.domain.risk.controller.dto.RiskResponses.RiskHistoryPoint;
import com.disaster.alert.alertapi.domain.risk.repository.RegionRiskHistoryRepository;
import com.disaster.alert.alertapi.domain.risk.repository.RegionRiskIndexRepository;
import com.disaster.alert.alertapi.domain.risk.repository.RegionRiskQueryRepository;
import com.disaster.alert.alertapi.domain.risk.model.RegionRiskIndex;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 지역 위험도 조회(읽기 전용) 애플리케이션 서비스.
 *
 * <p>컨트롤러가 리포지토리를 직접 의존하지 않도록 조회 오케스트레이션을 담당하고,
 * 영속 계층 read model → 응답 DTO 변환도 여기서 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionRiskQueryService {

    private final RegionRiskIndexRepository indexRepo;
    private final RegionRiskHistoryRepository historyRepo;
    private final RegionRiskQueryRepository queryRepo;

    /** 전국 시군구 위험도 (히트맵). */
    public List<RegionRiskMapItem> riskMap() {
        return indexRepo.findAll().stream()
                .map(r -> new RegionRiskMapItem(
                        r.getRegionCode(), r.getRiskScore(), r.getTopEventId(), r.getUpdatedAt()))
                .toList();
    }

    /** 특정 시군구 상세 + 기여 이벤트들. index 가 없으면 0 점으로 응답. */
    public RegionRiskDetail regionRisk(String regionCode) {
        RegionRiskIndex idx = indexRepo.findById(regionCode).orElse(null);
        double score = idx != null ? idx.getRiskScore() : 0.0;
        Long topEventId = idx != null ? idx.getTopEventId() : null;
        LocalDateTime updatedAt = idx != null ? idx.getUpdatedAt() : LocalDateTime.now();

        LocalDateTime since = LocalDateTime.now().minusDays(RiskConstants.ACTIVE_WINDOW_DAYS);
        List<ContributingEvent> contributing = queryRepo.findContributingEvents(regionCode, since).stream()
                .map(row -> new ContributingEvent(
                        row.eventId(), row.eventTitle(), row.disasterType(), row.impactScore()))
                .toList();

        return new RegionRiskDetail(regionCode, score, topEventId, contributing, updatedAt);
    }

    /** 시군구 위험도 시계열 (트렌드 / Phase 2 Chronos UI). */
    public List<RiskHistoryPoint> history(String regionCode, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return historyRepo.findSeries(regionCode, since).stream()
                .map(h -> new RiskHistoryPoint(h.getId().getSnapshotAt(), h.getRiskScore()))
                .toList();
    }
}
