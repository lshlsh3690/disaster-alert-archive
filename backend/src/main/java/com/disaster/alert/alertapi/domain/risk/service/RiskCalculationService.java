package com.disaster.alert.alertapi.domain.risk.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import com.disaster.alert.alertapi.domain.event.repository.DisasterEventRepository;
import com.disaster.alert.alertapi.domain.risk.RiskConstants;
import com.disaster.alert.alertapi.domain.risk.model.DisasterRiskProfile;
import com.disaster.alert.alertapi.domain.risk.model.RiskScore;
import com.disaster.alert.alertapi.domain.risk.repository.DisasterRiskProfileRepository;
import com.disaster.alert.alertapi.domain.risk.repository.EventRegionImpactRepository;
import com.disaster.alert.alertapi.domain.risk.repository.IntensityBracketRepository;
import com.disaster.alert.alertapi.domain.risk.repository.RegionRiskIndexRepository;
import com.disaster.alert.alertapi.domain.risk.repository.projection.EventImpactRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 재난 위험도 산출 핵심 서비스 — event 단위 5차원.
 *
 * <pre>
 * baseScore(event)        = weight[유형] × intensity[강도] × severity[정부등급]
 * riskScore(region, t)    = MAX over active events { baseScore × exp(-Δt·ln2 / half_life) }
 * normalized              = min(1.0, riskScore / 2.0)   // 0~1 보장
 * </pre>
 *
 * <p><b>두 경로 분리:</b>
 * <ol>
 *   <li>{@link #recomputeEventRisk}: 알림→클러스터링 후 호출. event_region_impact 기록까지만.
 *       region_risk_index 는 절대 건드리지 않는다(자기호출 @Transactional 무효·long-tx 회피).</li>
 *   <li>{@link #recomputeRegionRisk}: 스케줄러가 호출. 시간 감쇠 적용 + 시군구 집계 + index 갱신.
 *       active impact 없으면 0 으로 내림(만료 지역 staleness 방지).</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RiskCalculationService {

    private final DisasterEventRepository eventRepo;
    private final DisasterAlertRepository alertRepo;
    private final DisasterRiskProfileRepository profileRepo;
    private final IntensityBracketRepository bracketRepo;
    private final EventRegionImpactRepository impactRepo;
    private final RegionRiskIndexRepository indexRepo;
    private final IntensityExtractor intensityExtractor;
    private final LlmRiskProfiler llmProfiler;

    // ===== 경로 1: 알림 → 이벤트 영향 기록 =====

    /**
     * 이벤트의 baseScore 를 계산하여 영향 법정동들에 event_region_impact upsert.
     *
     * <p>region_risk_index 자체는 갱신하지 않는다(자기호출 @Transactional 무효 회피).
     * 대신 영향받은 <b>시군구 코드 집합을 반환</b>하여, 호출자(리스너)가 그 지역만
     * 별도 프록시 호출로 즉시 재계산하게 한다 → 문자 도착 시 실시간 갱신.
     *
     * @return 영향받은 시군구 코드 집합 (없으면 빈 집합)
     */
    @Transactional
    public Set<String> recomputeEventRisk(Long eventId) {
        DisasterEvent event = eventRepo.findById(eventId).orElse(null);
        if (event == null) {
            log.warn("recomputeEventRisk: eventId={} 조회 실패 — skip", eventId);
            return Set.of();
        }

        // 알림 + 영향 법정동 fetch join (N+1 방지)
        List<DisasterAlert> alerts = alertRepo.findByEventId(eventId);
        if (alerts.isEmpty()) {
            log.warn("recomputeEventRisk: eventId={} 매핑 알림 없음 — skip", eventId);
            return Set.of();
        }

        String rawType = event.getPrimaryDisasterType();
        final String finalType = "UNKNOWN".equals(rawType) ? "기타" : rawType;

        // 1. 유형 프로파일 (룰 → LLM fallback)
        DisasterRiskProfile profile = profileRepo.findById(finalType)
                .orElseGet(() -> llmProfiler.resolveForEvent(event));

        // 2. 강도 (event 알림들 중 최대)
        double intensityMult = alerts.stream()
                .map(intensityExtractor::extract)
                .flatMap(Optional::stream)
                .max(Double::compare)
                .flatMap(v -> bracketRepo.findMultiplier(finalType, v))
                .orElse(1.0);

        // 3. 정부 등급 (event 알림들 최고)
        double severityMult = alerts.stream()
                .map(a -> RiskScore.severityMultiplier(a.getEmergencyLevel()))
                .max(Double::compare)
                .orElse(1.0);

        // 4. baseScore (도메인 규칙은 RiskScore 값 객체가 소유)
        double baseScore = RiskScore.of(profile.getBaseWeight(), intensityMult, severityMult).baseScore();

        // 5. 영향 법정동 union (정부 broadcast 그대로 — 공간 추론 없음)
        Set<String> regions = alerts.stream()
                .flatMap(a -> a.getDisasterAlertRegions().stream())
                .map(r -> r.getId().getDistrictCode())
                .filter(Objects::nonNull)
                .filter(c -> !c.isBlank())
                .collect(Collectors.toSet());

        if (regions.isEmpty()) {
            log.info("recomputeEventRisk: eventId={} 영향 지역 없음 — impact 미기록", eventId);
            return Set.of();
        }

        // 6. event_region_impact upsert (멱등)
        regions.forEach(region -> impactRepo.upsert(eventId, region, round3(baseScore)));

        log.debug("recomputeEventRisk: eventId={} type={} base={} regions={}",
                eventId, finalType, baseScore, regions.size());

        // 7. 영향받은 시군구 집합 반환 (법정동 앞 5자리). 리스너가 이 지역만 즉시 재계산.
        return regions.stream()
                .map(code -> code.substring(0, Math.min(5, code.length())))
                .collect(Collectors.toSet());
    }

    // ===== 경로 2: 지역(시군구) 위험도 갱신 =====

    /**
     * 시군구 위험도 재계산: 법정동 impact 들을 시군구로 집계 + 시간 감쇠 + MAX + 정규화.
     * active impact 없으면 0 으로 upsert(만료 지역 zeroing).
     */
    @Transactional
    public void recomputeRegionRisk(String sigunguCode) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusDays(RiskConstants.ACTIVE_WINDOW_DAYS);

        List<EventImpactRow> rows = impactRepo.findActiveBySigungu(sigunguCode, since);
        if (rows.isEmpty()) {
            indexRepo.upsert(sigunguCode, 0.0, null, now);
            return;
        }

        EventImpactRow top = null;
        double topDecayed = -1.0;
        for (EventImpactRow row : rows) {
            double decayed = applyDecay(row, now);
            if (decayed > topDecayed) {
                topDecayed = decayed;
                top = row;
            }
        }

        double normalized = RiskScore.normalize(topDecayed);
        indexRepo.upsert(sigunguCode, round3(normalized),
                top != null ? top.getEventId() : null, now);
    }

    // ===== 내부 헬퍼 =====

    private double applyDecay(EventImpactRow row, LocalDateTime now) {
        int halfLife = profileRepo.findHalfLife(row.getDisasterType())
                .orElse(RiskConstants.DEFAULT_HALF_LIFE_HOURS);
        long hours = Duration.between(row.getLastAlertAt(), now).toHours();
        return new RiskScore(row.getImpactScore()).decayed(hours, halfLife);
    }

    private double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
