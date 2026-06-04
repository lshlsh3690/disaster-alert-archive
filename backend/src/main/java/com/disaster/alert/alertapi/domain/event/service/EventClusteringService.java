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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    @Value("${clustering.candidate-time-window-hours:168}")
    private int candidateWindowHours;

    /** 한 알림이 이 수를 초과하는 시군구에 발송되면 광역 브로드캐스트로 보고 단독 이벤트 처리. */
    @Value("${clustering.max-region-span:10}")
    private int maxRegionSpan;

    /** 광역 알림이 이 수 이상 시도에 걸치면 '전국' 이벤트로 분류 (유형만 키). 미만이면 시도 광역. */
    @Value("${clustering.nationwide-sido-span:8}")
    private int nationwideSidoSpan;

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

    /**
     * 배치 임베딩 생성 + 저장 (클러스터링 X).
     *
     * <p>백필 시 알림 1건마다 순차 호출하면 43k건에 수 시간 걸림. OpenAI 임베딩 API 는
     * 한 번에 여러 입력을 받으므로 묶어서 호출 → 호출 수 1/배치크기로 감소.
     * 임베딩은 알림별 독립이라 배치 가능(클러스터링은 순서 의존이라 별도 단계).
     *
     * @return 저장한 건수
     */
    @Transactional
    public int storeEmbeddings(List<Long> alertIds, List<String> messages) {
        List<float[]> vectors = embeddingModel.embed(messages);
        for (int i = 0; i < alertIds.size(); i++) {
            alertEmbeddingRepository.updateEmbedding(alertIds.get(i), toVectorText(vectors.get(i)));
        }
        return alertIds.size();
    }

    private void doCluster(DisasterAlert alert) {
        // 1. 임베딩 확보 — 이미 저장돼 있으면 재사용(OpenAI 재호출 X), 없으면 생성 후 저장.
        //    덕분에 재클러스터링(임계값 튜닝)이 공짜+빠름: 비싼 임베딩은 1회만.
        String embeddingText = alertEmbeddingRepository.findEmbeddingText(alert.getId());
        if (embeddingText == null) {
            float[] embedding = embeddingModel.embed(alert.getMessage());
            embeddingText = toVectorText(embedding);
            alertEmbeddingRepository.updateEmbedding(alert.getId(), embeddingText);
        }

        // 3. 후보 이벤트 검색
        String[] regionCodes = extractRegionCodes(alert);
        if (regionCodes.length == 0) {
            // 지역 정보 없으면 후보 검색 불가 → 항상 신규 이벤트
            log.info("clusterNewAlert: alertId={} 지역 코드 없음 — 신규 이벤트 생성", alert.getId());
            createNewEvent(alert, null, null, false);
            return;
        }

        // 광역 브로드캐스트(다수 시군구 발송)는 local 이벤트와 안 섞음 — blob 다리 차단(규칙 1·2).
        // 단, 같은 시도+유형 broadcast 끼리는 묶어 "{시도} {유형}" 단일 사건으로 통합(파편 방지).
        int sigunguSpan = distinctSigunguCount(regionCodes);
        if (sigunguSpan > maxRegionSpan) {
            clusterBroadcast(alert, regionCodes, sigunguSpan);
            return;
        }

        LocalDateTime since = alert.getCreatedAt().minusHours(candidateWindowHours);
        // 후보는 이미 지역 hard 필터(시군구 교집합) 통과한 같은 지역 이벤트들.
        List<Object[]> candidates = disasterEventRepository.findTopCandidates(embeddingText, regionCodes, since);

        // 4. top-3 후보 로깅 (임계값 튜닝 / 디버깅용)
        logCandidates(alert.getId(), candidates);

        // 5. 임계값 판정 — 같은 지역 후보 중 코사인 유사도 >= threshold 면 머지
        double mergeMaxDistance = 1.0 - similarityThreshold;
        for (Object[] row : candidates) {
            Long eventId = ((Number) row[0]).longValue();
            double dist = ((Number) row[1]).doubleValue();
            if (dist <= mergeMaxDistance) {
                mergeIntoExisting(eventId, alert, dist);
                return;
            }
        }

        createNewEvent(alert, regionCodes[0], extractFirstRegionName(alert), false);
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
            sb.append(String.format(Locale.ROOT, "[#%d event=%d dist=%.4f] ",
                    i + 1,
                    ((Number) c[0]).longValue(),
                    ((Number) c[1]).doubleValue()));
        }
        log.info("clusterNewAlert: alertId={} top-{} 후보: {}", alertId, candidates.size(), sb.toString().trim());
    }

    private void mergeIntoExisting(Long eventId, DisasterAlert alert, double distance) {
        int nextSeq = eventAlertMappingRepository.countByEventId(eventId) + 1;
        eventAlertMappingRepository.save(EventAlertMapping.of(eventId, alert.getId(), nextSeq));
        disasterEventRepository.incrementOnMerge(eventId, alert.getCreatedAt());
        log.info("clusterNewAlert: alertId={} → event={} 머지 (dist={}, seq={})",
                alert.getId(), eventId, distance, nextSeq);
    }

    private void createNewEvent(DisasterAlert alert, String regionCode, String regionName, boolean broadcast) {
        DisasterEvent event = DisasterEvent.createFromFirstAlert(
                alert.getDisasterType(),
                regionCode,
                regionName,
                alert.getMessage(),
                alert.getCreatedAt(),
                broadcast
        );
        DisasterEvent saved = disasterEventRepository.save(event);
        eventAlertMappingRepository.save(EventAlertMapping.of(saved.getId(), alert.getId(), 1));
        log.info("clusterNewAlert: alertId={} → 신규 event={} (broadcast={}, title='{}')",
                alert.getId(), saved.getId(), broadcast, saved.getEventTitle());
    }

    /**
     * 광역 broadcast 알림 처리. local 이벤트와는 절대 안 섞고(규칙 1·2), 같은 broadcast 끼리만 묶는다.
     * 묶음 키는 시도 범위에 따라 두 갈래:
     * <ul>
     *   <li><b>전국</b> (시도 span &ge; {@code nationwide-sido-span}): 유형만 키, 라벨 "전국 {유형}",
     *       {@code primary_region_code=null}. 행안부 전국 호우/대설 안내가 시도별로 흩어지지 않게 통합.</li>
     *   <li><b>시도 광역</b> (그 미만): (최다 시군구 시도 + 유형) 키. 첫 지역이 아니라 <b>최다 시군구
     *       시도</b>를 써서 지역 목록 순서에 흔들리지 않게 함.</li>
     * </ul>
     *
     * <p>임베딩(본문)은 보지 않는다 — "발효/해제/격상" 처럼 본문이 달라도 같은 사건으로 묶기 위함.
     * 유형이 '기타'/null 이면 본문이 제각각(물놀이 안전수칙·댐방류 등)이라 머지하지 않고 신규로 둔다.
     */
    private void clusterBroadcast(DisasterAlert alert, String[] regionCodes, int sigunguSpan) {
        String type = alert.getDisasterType();
        boolean informative = DisasterEvent.isInformativeType(type);
        LocalDateTime since = alert.getCreatedAt().minusHours(candidateWindowHours);
        int sidoSpan = distinctSidoCount(regionCodes);

        // 전국 — 유형만 키
        if (sidoSpan >= nationwideSidoSpan) {
            if (informative) {
                Optional<DisasterEvent> target = disasterEventRepository
                        .findFirstByBroadcastTrueAndPrimaryDisasterTypeAndPrimaryRegionCodeIsNullAndLastAlertAtAfterOrderByLastAlertAtDesc(
                                type, since);
                if (target.isPresent()) {
                    log.info("clusterNewAlert: alertId={} 전국({}시도) → broadcast event={} 유형 머지({})",
                            alert.getId(), sidoSpan, target.get().getId(), type);
                    mergeIntoExisting(target.get().getId(), alert, 0.0);
                    return;
                }
            }
            log.info("clusterNewAlert: alertId={} 전국({}시도) — 신규 broadcast 이벤트", alert.getId(), sidoSpan);
            createNewEvent(alert, null, "전국", true);
            return;
        }

        // 시도 광역 — (최다 시군구 시도 + 유형) 키
        String sido = dominantSidoPrefix(regionCodes);
        if (informative) {
            Optional<DisasterEvent> target = disasterEventRepository
                    .findFirstByBroadcastTrueAndPrimaryDisasterTypeAndPrimaryRegionCodeStartingWithAndLastAlertAtAfterOrderByLastAlertAtDesc(
                            type, sido, since);
            if (target.isPresent()) {
                log.info("clusterNewAlert: alertId={} 광역({}시군구) → broadcast event={} 시도+유형 머지({} {})",
                        alert.getId(), sigunguSpan, target.get().getId(), sido, type);
                mergeIntoExisting(target.get().getId(), alert, 0.0);
                return;
            }
        }
        log.info("clusterNewAlert: alertId={} 광역({}시군구>{}, {}시도) — 신규 broadcast 이벤트",
                alert.getId(), sigunguSpan, maxRegionSpan, sidoSpan);
        createNewEvent(alert, firstCodeOfSido(regionCodes, sido), sidoNameForPrefix(alert, sido), true);
    }

    /** 시도 코드(법정동 코드 앞 2자) — broadcast 시도 키. */
    private String sidoPrefix(String code) {
        return code != null && code.length() >= 2 ? code.substring(0, 2) : code;
    }

    /** 알림이 걸친 distinct 시도 수 — 전국/시도광역 분류용. */
    private int distinctSidoCount(String[] regionCodes) {
        java.util.Set<String> sido = new java.util.HashSet<>();
        for (String code : regionCodes) sido.add(sidoPrefix(code));
        return sido.size();
    }

    /** 가장 많은 시군구를 차지한 시도 prefix — 지역 목록 순서에 무관한 안정적 키. */
    private String dominantSidoPrefix(String[] regionCodes) {
        Map<String, Integer> count = new HashMap<>();
        for (String code : regionCodes) count.merge(sidoPrefix(code), 1, Integer::sum);
        return count.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(sidoPrefix(regionCodes[0]));
    }

    /** 지정 시도에 속한 첫 지역 코드 — primary_region_code 로 저장(StartingWith 매칭용). */
    private String firstCodeOfSido(String[] regionCodes, String sido) {
        for (String code : regionCodes) {
            if (sido.equals(sidoPrefix(code))) return code;
        }
        return regionCodes[0];
    }

    /** 지정 시도에 속한 지역명의 시도 토큰. 예: prefix "48" → "경상남도". */
    private String sidoNameForPrefix(DisasterAlert alert, String sido) {
        List<DisasterAlertRegion> regions = alert.getDisasterAlertRegions();
        if (regions == null) return null;
        for (DisasterAlertRegion r : regions) {
            String code = r.getId().getDistrictCode();
            if (code != null && sido.equals(sidoPrefix(code)) && r.getLegalDistrict() != null) {
                String name = r.getLegalDistrict().getName();
                if (name == null) return null;
                int sp = name.indexOf(' ');
                return sp < 0 ? name : name.substring(0, sp);
            }
        }
        return null;
    }

    /** 시군구(코드 앞 5자) 기준 distinct 개수 — 광역 알림 판정용. */
    private int distinctSigunguCount(String[] regionCodes) {
        java.util.Set<String> sigungu = new java.util.HashSet<>();
        for (String code : regionCodes) {
            sigungu.add(code.length() >= 5 ? code.substring(0, 5) : code);
        }
        return sigungu.size();
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
