package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlertRegion;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import com.disaster.alert.alertapi.domain.event.model.EventAlertMapping;
import com.disaster.alert.alertapi.domain.event.repository.AlertEmbeddingRepository;
import com.disaster.alert.alertapi.domain.event.repository.DisasterEventRepository;
import com.disaster.alert.alertapi.domain.event.repository.EventAlertMappingRepository;
import com.disaster.alert.alertapi.domain.legaldistrict.model.LegalDistrict;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 이벤트 클러스터링 핵심 서비스.
 *
 * <p>흐름 (새 알림 1건):
 * <ol>
 *   <li>OpenAI Embedding API → float[512] 벡터</li>
 *   <li>disaster_alert.embedding UPDATE</li>
 *   <li>후보 이벤트 검색 (7일 윈도우 + 지역 교집합 + 코사인 최소)</li>
 *   <li>최소 거리 &le; (1 - threshold) → 기존 이벤트 머지. 아니면 신규 이벤트.</li>
 * </ol>
 *
 * <p>{@code clustering.enabled=false} 인 경우 no-op (코드만 머지된 상태에서 안전 가드).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventClusteringService {

    private final EmbeddingModel embeddingModel;
    private final DisasterAlertRepository disasterAlertRepository;
    private final DisasterEventRepository disasterEventRepository;
    private final EventAlertMappingRepository eventAlertMappingRepository;
    private final AlertEmbeddingRepository alertEmbeddingRepository;

    @Value("${clustering.enabled:false}")
    private boolean enabled;

    @Value("${clustering.similarity-threshold:0.85}")
    private double similarityThreshold;

    @Value("${clustering.cross-region-similarity-threshold:0.93}")
    private double crossRegionSimilarityThreshold;

    @Value("${clustering.candidate-time-window-hours:168}")
    private int candidateWindowHours;

    /**
     * 신규 알림에 대해 임베딩 생성 + 클러스터링 수행.
     *
     * <p>{@code clustering.enabled=false} 또는 alert/message 가 비어있으면 조용히 skip.
     * 외부 API 실패 시 예외 던지지 않음 — 수집 스케줄러의 다음 사이클이나 백필 도구로 복구.
     */
    @Transactional
    public void clusterNewAlert(Long alertId) {
        if (!enabled) {
            return;
        }

        Optional<DisasterAlert> opt = disasterAlertRepository.findById(alertId);
        if (opt.isEmpty()) {
            log.warn("clusterNewAlert: alertId={} 조회 실패", alertId);
            return;
        }
        DisasterAlert alert = opt.get();
        if (alert.getMessage() == null || alert.getMessage().isBlank()) {
            log.warn("clusterNewAlert: alertId={} message 가 비어있음 — skip", alertId);
            return;
        }

        try {
            doCluster(alert);
        } catch (Exception e) {
            // 한 건 실패가 수집 사이클을 막지 않도록 여기서 잡음.
            log.error("clusterNewAlert: alertId={} 처리 중 오류 — skip", alertId, e);
        }
    }

    private void doCluster(DisasterAlert alert) {
        // 1. 임베딩 생성
        float[] embedding = embeddingModel.embed(alert.getMessage());
        String embeddingText = toVectorText(embedding);

        // 2. disaster_alert.embedding UPDATE
        alertEmbeddingRepository.updateEmbedding(alert.getId(), embeddingText);

        // 3. 후보 이벤트 검색
        String[] regionCodes = extractRegionCodes(alert);
        if (regionCodes.length == 0) {
            // 지역 정보 없으면 후보 검색 불가 → 항상 신규 이벤트
            log.info("clusterNewAlert: alertId={} 지역 코드 없음 — 신규 이벤트 생성", alert.getId());
            createNewEvent(alert, null, null);
            return;
        }

        LocalDateTime since = alert.getCreatedAt().minusHours(candidateWindowHours);
        List<Object[]> candidates = disasterEventRepository.findTopCandidates(embeddingText, regionCodes, since);

        // 4. top-3 후보 로깅 (임계값 튜닝 / 디버깅용)
        logCandidates(alert.getId(), candidates);

        // 5. 임계값 판정 — 지역 겹침 여부에 따라 다른 임계값 적용
        for (Object[] row : candidates) {
            Long eventId = ((Number) row[0]).longValue();
            double dist = ((Number) row[1]).doubleValue();
            boolean regionOverlap = (Boolean) row[2];

            double threshold = regionOverlap ? similarityThreshold : crossRegionSimilarityThreshold;
            double mergeMaxDistance = 1.0 - threshold;

            if (dist <= mergeMaxDistance) {
                mergeIntoExisting(eventId, alert, dist, regionOverlap);
                return;
            }
        }

        createNewEvent(alert, regionCodes[0], extractFirstRegionName(alert));
    }

    /**
     * top-3 후보 정보 로깅. 어떤 후보가 임계값을 못 넘었는지 알아야 임계값 튜닝 가능.
     */
    private void logCandidates(Long alertId, List<Object[]> candidates) {
        if (candidates.isEmpty()) {
            log.info("clusterNewAlert: alertId={} 후보 0건 (윈도우 안에 이벤트 없음)", alertId);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            Object[] c = candidates.get(i);
            sb.append(String.format(Locale.ROOT, "[#%d event=%d dist=%.4f overlap=%s] ",
                    i + 1,
                    ((Number) c[0]).longValue(),
                    ((Number) c[1]).doubleValue(),
                    c[2]));
        }
        log.info("clusterNewAlert: alertId={} top-{} 후보: {}", alertId, candidates.size(), sb.toString().trim());
    }

    private void mergeIntoExisting(Long eventId, DisasterAlert alert, double distance, boolean regionOverlap) {
        int nextSeq = eventAlertMappingRepository.countByEventId(eventId) + 1;
        eventAlertMappingRepository.save(EventAlertMapping.of(eventId, alert.getId(), nextSeq));
        disasterEventRepository.incrementOnMerge(eventId, alert.getCreatedAt());
        log.info("clusterNewAlert: alertId={} → event={} 머지 (dist={}, overlap={}, seq={})",
                alert.getId(), eventId, distance, regionOverlap, nextSeq);
    }

    private void createNewEvent(DisasterAlert alert, String regionCode, String regionName) {
        DisasterEvent event = DisasterEvent.createFromFirstAlert(
                alert.getDisasterType(),
                regionCode,
                regionName,
                alert.getMessage(),
                alert.getCreatedAt()
        );
        DisasterEvent saved = disasterEventRepository.save(event);
        eventAlertMappingRepository.save(EventAlertMapping.of(saved.getId(), alert.getId(), 1));
        log.info("clusterNewAlert: alertId={} → 신규 event={} (title='{}')",
                alert.getId(), saved.getId(), saved.getEventTitle());
    }

    private String[] extractRegionCodes(DisasterAlert alert) {
        List<DisasterAlertRegion> regions = alert.getDisasterAlertRegions();
        if (regions == null || regions.isEmpty()) return new String[0];
        List<String> codes = new ArrayList<>();
        for (DisasterAlertRegion r : regions) {
            String code = r.getId().getDistrictCode();
            if (code != null && !code.isBlank()) codes.add(code);
        }
        return codes.toArray(new String[0]);
    }

    private String extractFirstRegionName(DisasterAlert alert) {
        List<DisasterAlertRegion> regions = alert.getDisasterAlertRegions();
        if (regions == null || regions.isEmpty()) return null;
        DisasterAlertRegion first = regions.get(0);
        LegalDistrict ld = first.getLegalDistrict();
        return ld == null ? null : ld.getName();
    }

    /**
     * float[] → pgvector text 표현. 예: "[0.013,-0.421,0.187]".
     * 공백/줄바꿈 없이 콤마로만 구분 — INSERT/UPDATE 양쪽에서 동일 형식 사용.
     */
    private static String toVectorText(float[] embedding) {
        StringBuilder sb = new StringBuilder(embedding.length * 12);
        sb.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            // Locale.ROOT 로 소수점 . 보장 (한국 로케일 ',' 회피)
            sb.append(String.format(Locale.ROOT, "%.6f", embedding[i]));
        }
        sb.append(']');
        return sb.toString();
    }
}
