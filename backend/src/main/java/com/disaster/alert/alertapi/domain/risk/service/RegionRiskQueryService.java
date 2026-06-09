package com.disaster.alert.alertapi.domain.risk.service;

import com.disaster.alert.alertapi.domain.risk.RiskConstants;
import com.disaster.alert.alertapi.domain.risk.controller.dto.RiskResponses.AlertRiskResponse;
import com.disaster.alert.alertapi.domain.risk.controller.dto.RiskResponses.ContributingEvent;
import com.disaster.alert.alertapi.domain.risk.controller.dto.RiskResponses.RegionRiskDetail;
import com.disaster.alert.alertapi.domain.risk.controller.dto.RiskResponses.RegionRiskMapItem;
import com.disaster.alert.alertapi.domain.risk.controller.dto.RiskResponses.RiskHistoryPoint;
import com.disaster.alert.alertapi.domain.risk.repository.projection.AlertRiskRow;
import com.disaster.alert.alertapi.domain.risk.repository.RegionRiskHistoryRepository;
import com.disaster.alert.alertapi.domain.risk.repository.RegionRiskIndexRepository;
import com.disaster.alert.alertapi.domain.risk.repository.RegionRiskQueryRepository;
import com.disaster.alert.alertapi.domain.risk.model.RegionRiskIndex;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
    private final com.disaster.alert.alertapi.domain.risk.repository.RegionRiskDailyRepository dailyRepo;
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

    /** 과거 특정 기간 내 시군구 기여 이벤트 목록 (히트맵 클릭 상세용). */
    public List<ContributingEvent> historicalEvents(String regionCode, LocalDateTime start, LocalDateTime end) {
        return queryRepo.findHistoricalEvents(regionCode, start, end).stream()
                .map(row -> new ContributingEvent(
                        row.eventId(), row.eventTitle(), row.disasterType(), row.impactScore()))
                .toList();
    }

    /** 기간 내 전국 시군구 위험도 프레임 (타임슬라이더 히트맵). */
    public java.util.Map<String, List<RegionRiskMapItem>> historicalMap(LocalDateTime start, LocalDateTime end) {
        // 최근 90일(Retention) 범위인지 확인하여 Hourly / Daily 결정
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RiskConstants.HISTORY_RETENTION_DAYS);
        
        if (start.isAfter(cutoff)) {
            // 전부 최근 90일 이내면 시간 단위(Hourly) 상세 프레임 제공
            return historyRepo.findMapSeries(start, end).stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            h -> h.getId().getSnapshotAt().toString(),
                            java.util.TreeMap::new,
                            java.util.stream.Collectors.mapping(
                                    h -> new RegionRiskMapItem(
                                            h.getId().getRegionCode(), h.getRiskScore(), null, h.getId().getSnapshotAt()),
                                    java.util.stream.Collectors.toList()
                            )
                    ));
        } else {
            // 90일을 넘어가는 과거면 일 단위(Daily) 요약 프레임 제공
            return dailyRepo.findMapSeriesDaily(start.toLocalDate(), end.toLocalDate()).stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            d -> d.getId().getSnapshotDate().atStartOfDay().toString(),
                            java.util.TreeMap::new,
                            java.util.stream.Collectors.mapping(
                                    d -> new RegionRiskMapItem(
                                            d.getId().getRegionCode(), d.getRiskScore(), null, d.getId().getSnapshotDate().atStartOfDay()),
                                    java.util.stream.Collectors.toList()
                            )
                    ));
        }
    }

    /**
     * 재난문자 단건 위험도.
     * alert가 이벤트에 클러스터링되지 않았거나 아직 위험도 계산 전이면 empty.
     */
    public Optional<AlertRiskResponse> alertRisk(Long alertId) {
        List<AlertRiskRow> rows = queryRepo.findByAlertId(alertId);
        if (rows.isEmpty()) return Optional.empty();

        AlertRiskRow first = rows.get(0);
        List<AlertRiskResponse.RegionImpact> impacts = rows.stream()
                .map(r -> new AlertRiskResponse.RegionImpact(r.regionCode(), r.impactScore()))
                .toList();
        return Optional.of(new AlertRiskResponse(alertId, first.eventId(), first.eventTitle(), first.disasterType(), impacts));
    }

    /** 시군구 위험도 시계열 (트렌드 / Phase 2 Chronos UI). */
    public List<RiskHistoryPoint> history(String regionCode, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return historyRepo.findSeries(regionCode, since).stream()
                .map(h -> new RiskHistoryPoint(h.getId().getSnapshotAt(), h.getRiskScore()))
                .toList();
    }
}
