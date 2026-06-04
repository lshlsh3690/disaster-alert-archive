package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import com.disaster.alert.alertapi.domain.event.model.MergeMethod;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 기타(지역 이동 유형) cross-region LLM 병합.
 *
 * <p>{@link EventClusteringService#clusterNewAlert}(local + broadcast)는 LLM-free 로 유지하고,
 * cross-region 은 이 별도 단계로만 실행한다 → recluster(공짜 튜닝 루프)에 LLM 이 안 섞인다.
 *
 * <p>흐름: 게이트(기타 + mover 키워드) → 대상 알림의 현재 이벤트 확인 → 지역필터 뺀 mover 이벤트
 * 임베딩 top-K 후보 → {@link EventLLMDecisionService} 동일 인물/개체 판정 → span-cap 검사 후
 * {@link EventClusteringService#mergeEvents}. {@code clustering.cross-region.enabled=false} 면 no-op.
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

    @Value("${clustering.cross-region.mover-keywords:찾습니다|실종|수배|가출|배회|탈출|출몰|멧돼지|들개|늑대}")
    private String moverKeywords;

    private Pattern moverPattern;

    private Pattern moverPattern() {
        if (moverPattern == null) {
            moverPattern = Pattern.compile(moverKeywords);
        }
        return moverPattern;
    }

    /**
     * 대상 알림을 cross-region 기준으로 기존 mover 이벤트에 병합 시도.
     * 전제: 이미 {@link EventClusteringService#clusterNewAlert}로 어떤 이벤트에 속해 있음.
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
        if (!isMover(alert)) {
            return;
        }
        try {
            doLink(alert);
        } catch (Exception e) {
            // 한 건 실패가 수집/백필 사이클을 막지 않도록 격리.
            log.error("linkCrossRegion: alertId={} 처리 중 오류 — skip", alertId, e);
        }
    }

    /** 게이트: disaster_type='기타' AND mover 키워드 포함. */
    private boolean isMover(DisasterAlert alert) {
        if (!"기타".equals(alert.getDisasterType())) {
            return false;
        }
        String msg = alert.getMessage();
        return msg != null && moverPattern().matcher(msg).find();
    }

    private void doLink(DisasterAlert alert) {
        Long selfEventId = eventAlertMappingRepository.findEventIdByAlertId(alert.getId());
        if (selfEventId == null) {
            log.debug("linkCrossRegion: alertId={} 아직 클러스터링 안 됨 — skip", alert.getId());
            return;
        }
        // 이미 여러 시도에 걸친(= cross-region 링크된) 이벤트면 재처리 skip (idempotency).
        if (eventAlertMappingRepository.countDistinctSidoByEventId(selfEventId) >= 2) {
            return;
        }
        String embeddingText = alertEmbeddingRepository.findEmbeddingText(alert.getId());
        if (embeddingText == null) {
            return;
        }

        LocalDateTime since = alert.getCreatedAt().minusHours(windowHours);
        double maxDistance = 1.0 - similarityFloor;
        List<Object[]> rows = disasterEventRepository.findCrossRegionCandidates(
                embeddingText, since, selfEventId, moverKeywords, maxDistance, topK);
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
            log.info("linkCrossRegion: alertId={} (event={}) — LLM NONE (후보 {}건)",
                    alert.getId(), selfEventId, candidates.size());
            return;
        }
        Long targetEventId = candidates.get(pick).eventId();

        if (exceedsSpanCap(targetEventId, selfEventId)) {
            log.warn("linkCrossRegion: alertId={} target event={} span-cap 초과 — 병합 skip",
                    alert.getId(), targetEventId);
            return;
        }

        log.info("linkCrossRegion: alertId={} event {} → {} 같은 사건 판정 — 병합",
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
