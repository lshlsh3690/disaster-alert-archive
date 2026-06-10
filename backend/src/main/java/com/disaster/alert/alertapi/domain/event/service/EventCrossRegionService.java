package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlertRegion;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.event.model.AnimalIdentity;
import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import com.disaster.alert.alertapi.domain.event.model.MergeMethod;
import com.disaster.alert.alertapi.domain.event.model.MissingPersonIdentity;
import com.disaster.alert.alertapi.domain.event.repository.AlertEmbeddingRepository;
import com.disaster.alert.alertapi.domain.event.repository.DisasterEventRepository;
import com.disaster.alert.alertapi.domain.event.repository.EventAlertMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 동물·비정형 이동 사건 cross-region LLM 병합.
 *
 * <p>실종 인물은 신원(이름+나이+키)으로 {@link EventClusteringService#clusterNewAlert} 에서 결정적으로
 * 클러스터링되므로 여기서 다루지 않는다. 이 서비스는 <b>탈출 동물 등 정형 키가 없는</b> 이동 사건만
 * 임베딩 top-K + LLM 으로 판정·병합한다. {@code clusterNewAlert}(local) 와 분리된 별도 단계라
 * recluster(공짜 튜닝 루프)에 LLM 이 안 섞인다. {@code clustering.cross-region.enabled=false} 면 no-op.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventCrossRegionService {

    private final DisasterAlertRepository disasterAlertRepository;
    private final AlertEmbeddingRepository alertEmbeddingRepository;
    private final DisasterEventRepository disasterEventRepository;
    private final EventAlertMappingRepository eventAlertMappingRepository;
    private final EventLLMDecisionService llmDecisionService;
    private final EventClusteringService eventClusteringService;

    @Value("${clustering.cross-region.enabled:false}")
    private boolean enabled;

    @Value("${clustering.cross-region.candidate-window-hours:336}")
    private int windowHours;

    @Value("${clustering.cross-region.top-k:5}")
    private int topK;

    @Value("${clustering.cross-region.similarity-floor:0.6}")
    private double similarityFloor;

    @Value("${clustering.cross-region.span-cap-alerts:30}")
    private int spanCapAlerts;

    @Value("${clustering.cross-region.span-cap-sido:10}")
    private int spanCapSido;

    /**
     * 동물·비정형 이동 알림을 기존 이벤트에 cross-region 병합 시도.
     * 전제: 이미 {@link EventClusteringService#clusterNewAlert}로 어떤 이벤트(임베딩 클러스터)에 속해 있음.
     */
    @Transactional
    public void linkCrossRegion(Long alertId) {
        if (!enabled) {
            return;
        }
        Optional<DisasterAlert> opt = disasterAlertRepository.findById(alertId);
        if (opt.isEmpty()) {
            return;
        }
        DisasterAlert alert = opt.get();
        if (!isAnimalCase(alert)) {
            return;
        }
        try {
            linkAnimal(alert);
        } catch (Exception e) {
            // 한 건 실패가 수집/백필 사이클을 막지 않도록 격리.
            log.error("linkCrossRegion: alertId={} 처리 중 오류 — skip", alertId, e);
        }
    }

    /** 게이트: disaster_type='기타' AND 인물 아님(신원 클러스터링 대상 제외) AND 종 식별됨. */
    private boolean isAnimalCase(DisasterAlert alert) {
        if (!"기타".equals(alert.getDisasterType())) {
            return false;
        }
        String msg = alert.getMessage();
        if (msg == null || MissingPersonIdentity.isPerson(msg)) {
            return false;
        }
        return AnimalIdentity.species(msg) != null;
    }

    /** 대상 알림의 distinct 시도 코드(앞 2자) — 인접 게이트 기준. 순서 보존. */
    private List<String> extractSidoCodes(DisasterAlert alert) {
        List<DisasterAlertRegion> regions = alert.getDisasterAlertRegions();
        if (regions == null || regions.isEmpty()) {
            return List.of();
        }
        Set<String> codes = new LinkedHashSet<>();
        for (DisasterAlertRegion r : regions) {
            String code = r.getId().getDistrictCode();
            if (code != null && code.length() >= 2) {
                codes.add(code.substring(0, 2));
            }
        }
        return new ArrayList<>(codes);
    }

    /**
     * 동물·비정형 cross-region — 정형 키가 없어 임베딩 top-K 후보 + LLM 판정.
     * 탈출·출몰·늑대 등 건수가 적고 본문이 자유서술이라 LLM 이 유일한 수단.
     */
    private void linkAnimal(DisasterAlert alert) {
        Long selfEventId = eventAlertMappingRepository.findEventIdByAlertId(alert.getId());
        if (selfEventId == null) {
            return;
        }
        // 이미 여러 시도에 걸친(= cross-region 링크된) 이벤트면 재처리 skip (idempotency).
        if (eventAlertMappingRepository.countDistinctSidoByEventId(selfEventId) >= 2) {
            return;
        }
        // 종 하드게이트 — 식별 못 하면 cross-region 안 함(보수적). 다른 종끼리 묶이는 과병합 차단.
        String speciesRegex = AnimalIdentity.speciesRegex(AnimalIdentity.species(alert.getMessage()));
        if (speciesRegex == null) {
            return;
        }
        // 인접 게이트 기준 — 대상 알림 시도(앞 2자). 지역 없으면 인접 판정 불가 → skip.
        List<String> sidoCodes = extractSidoCodes(alert);
        if (sidoCodes.isEmpty()) {
            return;
        }
        String embeddingText = alertEmbeddingRepository.findEmbeddingText(alert.getId());
        if (embeddingText == null) {
            return;
        }
        LocalDateTime since = alert.getCreatedAt().minusHours(windowHours);
        List<Object[]> rows = disasterEventRepository.findCrossRegionCandidates(
                embeddingText, since, selfEventId, speciesRegex, sidoCodes, 1.0 - similarityFloor, topK);
        if (rows.isEmpty()) {
            return;
        }
        List<Long> candidateIds = new ArrayList<>();
        for (Object[] row : rows) {
            candidateIds.add(((Number) row[0]).longValue());
        }
        Map<Long, String> repMessages = representativeMessages(candidateIds);
        List<EventLLMDecisionService.Candidate> candidates = new ArrayList<>();
        for (Long id : candidateIds) {
            candidates.add(new EventLLMDecisionService.Candidate(id, repMessages.getOrDefault(id, "")));
        }

        Integer pick = llmDecisionService.pickSameIncident(alert.getMessage(), candidates);
        if (pick == null) {
            return;
        }
        Long targetEventId = candidates.get(pick).eventId();
        if (exceedsSpanCap(targetEventId, selfEventId)) {
            return;
        }
        log.info("linkCrossRegion(동물): alertId={} event {} → {} LLM 같은 사건 — 병합",
                alert.getId(), selfEventId, targetEventId);
        eventClusteringService.mergeEvents(selfEventId, targetEventId, MergeMethod.LLM);
    }

    private Map<Long, String> representativeMessages(List<Long> eventIds) {
        Map<Long, String> map = new HashMap<>();
        for (Object[] row : eventAlertMappingRepository.findRepresentativeMessages(eventIds)) {
            map.put(((Number) row[0]).longValue(), (String) row[1]);
        }
        return map;
    }

    /** target 이 self 를 흡수하면 너무 커지는지(알림 수/시도 수 상한) — blob 방지. */
    private boolean exceedsSpanCap(Long targetEventId, Long selfEventId) {
        DisasterEvent target = disasterEventRepository.findById(targetEventId).orElse(null);
        if (target == null) {
            return true;
        }
        int selfCount = eventAlertMappingRepository.countByEventId(selfEventId);
        if (target.getAlertCount() + selfCount > spanCapAlerts) {
            return true;
        }
        return eventAlertMappingRepository.countDistinctSidoByEventId(targetEventId) >= spanCapSido;
    }
}
