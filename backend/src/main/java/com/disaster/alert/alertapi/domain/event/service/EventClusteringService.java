package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlertRegion;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.event.model.AdvisoryClassifier;
import com.disaster.alert.alertapi.domain.event.model.DisasterCooldown;
import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import com.disaster.alert.alertapi.domain.event.model.EventAlertMapping;
import com.disaster.alert.alertapi.domain.event.model.MergeMethod;
import com.disaster.alert.alertapi.domain.event.model.MissingPersonIdentity;
import com.disaster.alert.alertapi.domain.event.repository.AlertEmbeddingRepository;
import com.disaster.alert.alertapi.domain.event.repository.DisasterEventRepository;
import com.disaster.alert.alertapi.domain.event.repository.EventAlertMappingRepository;
import com.disaster.alert.alertapi.domain.legaldistrict.model.LegalDistrict;
import com.disaster.alert.alertapi.domain.risk.event.AlertClusteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 이벤트 클러스터링 핵심 서비스.
 *
 * <p>흐름 (새 알림 1건):
 * <ol>
 *   <li>OpenAI Embedding API → float[1536] 벡터</li>
 *   <li>disaster_alert.embedding UPDATE</li>
 *   <li>후보 이벤트 검색 (7일 윈도우 + 지역 교집합 + 코사인 최소)</li>
 *   <li>최소 거리 &le; (1 - threshold) → 기존 이벤트 머지. 아니면 신규 이벤트.</li>
 * </ol>
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
    private final ApplicationEventPublisher eventPublisher;
    private final EventLLMDecisionService llmDecisionService;

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

    /** 실종 인물 신원 클러스터링 윈도우(시간). 같은 사람 재신고를 한 이벤트로 보는 간격. 기본 14일. */
    @Value("${clustering.person-window-hours:336}")
    private int personWindowHours;

    /** LLM 폴백 후보 거리 상한(코사인). (mergeMaxDistance, ceil] 구간 후보만 LLM 에 묻는다. */
    @Value("${clustering.llm-fallback.distance-ceil:0.40}")
    private double llmFallbackDistanceCeil;

    /** LLM 폴백 적용 유형(쉼표 구분) — 특정 장소·시설의 일회성 사고만. 폭염·호우 등 기상특보 제외(과병합 방지). */
    @Value("${clustering.llm-fallback.accident-types:기타,화재,산불,붕괴,교통사고,교통통제,교통,환경오염사고,정전,통신,테러,지진,지진해일,수도}")
    private String accidentTypesCsv;

    /** 동물·비정형 키워드 — LLM 폴백에서 제외(cross-region 단계가 담당). cross-region 설정과 공유. */
    @Value("${clustering.cross-region.animal-keywords:탈출|출몰|멧돼지|들개|늑대}")
    private String animalKeywords;

    /** 전국 통합 유형(쉼표 구분) — 지역 무관 단일 사건. 태풍처럼 시군구 단위로 쪼개져도 하나로 묶는다. */
    @Value("${clustering.global-types:태풍}")
    private String globalTypesCsv;

    /** 전국 통합 유형의 머지 윈도우(시간). 같은 윈도우 안의 같은 유형이면 한 사건. 기본 7일(168h). */
    @Value("${clustering.global-window-hours:168}")
    private int globalWindowHours;

    /**
     * 지역앵커 유형(유형:윈도우시간 CSV) — 산불·산사태·홍수처럼 유형+시군구가 곧 사건인 이산 재난.
     * 태풍(global-types, 전국)과 달리 시군구 스코프. 윈도우는 유형별로 다르다 — 산불은 재발화 주기가
     * 길어 14일(336h), 호우성(산사태·홍수)은 같은 날 burst 라 7일(168h). 계절 연속형(폭염·호우특보 등)은
     * 절대 넣지 말 것(같은 시군구 매일 발령 → span blob).
     */
    @Value("${clustering.regional-types:산불:336,산사태:168,홍수:168}")
    private String regionalTypesCsv;

    /**
     * 안내성 분리 적용 유형(쉼표 구분) — 안내성(예방·캠페인·일반 행동요령)이 실사건과 독립적이고 시즌 내내
     * 반복돼 사건 버킷을 늘리는 유형만. 현재 산불뿐 — 산사태·홍수는 안내(조기경보)가 같은 호우
     * episode 에 강결합이라 분리하면 타임라인이 깨진다(실데이터). 머지 윈도우는 regional-types 를 쓴다.
     * 폭염·한파는 안내를 분리하지 않고 episodic-types 로 특보와 한 이벤트에 합친다.
     */
    @Value("${clustering.advisory-split-types:산불}")
    private String advisorySplitTypesCsv;

    /** 임베딩 병합 임계값을 완화할 에피소드형 유형(쉼표 구분). 폭염·한파·대설·강풍·가축질병. */
    @Value("${clustering.relaxed-threshold-types:폭염,한파,대설,강풍,가축질병}")
    private String relaxedThresholdTypesCsv;

    /** 완화 대상 유형에 적용할 병합 유사도 하한(거리 상한 = 1 - 값). 기본 0.65 → 거리 0.35. */
    @Value("${clustering.relaxed-similarity-threshold:0.65}")
    private double relaxedSimilarityThreshold;

    /** 에피소드형 유형(쉼표 구분) — "유형 + 시군구" 단위 하나의 이벤트로 이어 가는 유형. */
    @Value("${clustering.episodic-types:폭염,한파,대설,강풍,가축질병}")
    private String episodicTypesCsv;

    /** 에피소드형 유형의 끊김 기준(시간) — 같은 유형+시군구의 마지막 알림 이후 이 시간이 지나면 새 이벤트. */
    @Value("${clustering.episodic-window-hours:168}")
    private int episodicWindowHours;

    /** 사고성 유형의 인접 시군구 후보 조회 윈도우(시간) — 같은 시군구 윈도우보다 짧게. */
    @Value("${clustering.adjacent-window-hours:48}")
    private int adjacentWindowHours;

    /** 인접 시군구 병합 허용 이벤트 나이 상한(시간) — 후보 이벤트 첫 알림부터 새 알림까지. 인접 경로 전용. */
    @Value("${clustering.adjacent-max-event-age-hours:72}")
    private int adjacentMaxEventAgeHours;

    /** 안내 이벤트 병합 윈도우(시간) — 안내성 유형(기타 등) 전용. 산불은 regional-types 윈도우를 쓴다. */
    @Value("${clustering.advisory-window-hours:168}")
    private int advisoryWindowHours;

    private Set<String> accidentTypes;
    private Pattern animalPattern;
    private Set<String> globalTypes;
    private Map<String, Integer> regionalTypeWindows;
    private Set<String> advisorySplitTypes;
    private Set<String> relaxedThresholdTypes;
    private Set<String> episodicTypes;

    /**
     * 신규 알림에 대해 임베딩 생성 + 클러스터링 수행.
     *
     * <p>alert/message 가 비어있으면 조용히 skip.
     * 외부 API 실패 시 예외 던지지 않음 — 수집 스케줄러의 다음 사이클이나 백필 도구로 복구.
     */
    @Transactional
    public void clusterNewAlert(Long alertId) {
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
        // 0. 실종 인물(기타 + 이름·나이)은 신원(이름+나이+키)으로만 클러스터링 — 시군구·임베딩 무관.
        //    같은 사람=한 이벤트(지역 옮겨도), 다른 사람=다른 이벤트(같은 시군구라도). broadcast 도 안 탐.
        if (clusterPerson(alert)) {
            return;
        }

        // 0.5 전국 통합 유형(태풍 등)은 지역·임베딩 무관하게 시간 윈도우로만 단일 이벤트에 묶는다.
        //     태풍 알림은 대부분 시군구 단위로 쪼개져 발송돼(span 게이트 미달) local 경로의 지역
        //     hard 필터에 막혀 시군구마다 이벤트가 생겼다. 유형 자체를 키로 써서 임베딩 전에 분기.
        if (clusterGlobalType(alert)) {
            return;
        }

        // 0.7 지역앵커 유형(산불·산사태·홍수) — 유형+시군구가 곧 사건. 같은 시군구·같은 유형·유형별
        //     윈도우 안이면 임베딩 무관하게 단일 이벤트로 묶는다. 한 산불/호우 episode 가 같은 시군구에서
        //     며칠 burst 로 쏟아질 때 본문 텍스트가 갈려(코사인<0.85) 임베딩 경로가 한 사건을 수십 개로
        //     파편화하던 것을 차단. 태풍(GLOBAL_TYPE)과 같은 철학이되 전국이 아니라 시군구 스코프(blob 방지).
        if (clusterRegionalType(alert)) {
            return;
        }

        // 0.8 에피소드형 유형(폭염·한파·대설·강풍·가축질병) — 같은 시군구·같은 유형의 발령이 끊김 없이
        //     이어지는 동안은 안내/특보·문장 유사도와 무관하게 하나의 이벤트. 임베딩·LLM 호출 전에 분기해
        //     비용도 들지 않는다. 시군구 1개는 앵커링, 2개 이상(다지역)은 지역 이벤트와 섞이지 않게
        //     곧바로 광역(broadcast) 경로로 보낸다(시군구 간 다리 방지). 사고성은 전파 허용이라 대상 아님.
        if (clusterEpisodicType(alert)) {
            return;
        }

        // 0.9 안내성 알림(물놀이·수상안전 예방 안내 등) — 시군구별 안내 이벤트로 모으고 이웃 병합은 안 한다.
        if (clusterAdvisoryType(alert)) {
            return;
        }

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
        // 코드 granularity 차이(군레벨 4886000000 vs 읍면동 4886036000)를 흡수하려 시군구(앞 5자) 교집합으로 매칭.
        // 같은 시군구·같은 사건이 읍면동 태깅 차이로 갈리던 과소병합(예: 산청 산불 시천면↔군레벨) 차단.
        List<Object[]> candidates = disasterEventRepository.findTopCandidates(embeddingText, sigunguPrefixes(regionCodes), since);

        // 4. top-3 후보 로깅 (임계값 튜닝 / 디버깅용)
        logCandidates(alert.getId(), candidates);

        // 5. 임계값 판정 — 같은 지역 후보 중 코사인 유사도 >= threshold 면 머지
        double mergeMaxDistance = mergeMaxDistanceFor(alert.getDisasterType());
        for (Object[] row : candidates) {
            Long eventId = ((Number) row[0]).longValue();
            double dist = ((Number) row[1]).doubleValue();
            if (dist <= mergeMaxDistance) {
                mergeIntoExisting(eventId, alert, 1.0 - dist, MergeMethod.EMBEDDING);
                return;
            }
        }

        // 5.2 인접 시군구 후보 — 사고성 유형만, 임계값 병합만(LLM 폴백 없음).
        if (tryMergeAdjacent(alert, embeddingText, regionCodes, mergeMaxDistance)) {
            return;
        }

        // 5.5 LLM 폴백 — 임베딩은 임계 미만이지만 같은 사건일 수 있는 사고성 알림을 LLM 으로 판정.
        //     "서소문 고가 붕괴"처럼 같은 사고를 여러 기관이 다른 측면(도로통제/열차중지)으로 보내
        //     본문 임베딩이 갈리는 케이스를 묶는다. 폭염 등 기상특보는 화이트리스트에서 제외(과병합 방지).
        if (tryLlmFallback(alert, candidates, mergeMaxDistance)) {
            return;
        }

        createNewEvent(alert, regionCodes[0], extractFirstRegionName(alert), false);
    }

    /**
     * 사고성 유형의 인접 시군구 후보 병합 — 같은 사고가 시군구 경계를 넘어 인접 시군구로 번지면 시군구가
     * 달라 같은 시군구 hard 필터에서 갈라지던 것을 잡는다.
     *
     * <p>임계값(mergeMaxDistance)만 쓰고 LLM 폴백은 태우지 않는다 — 지역 제약이 느슨해진 만큼 borderline 까지
     * 넓히면 별개 사고를 합칠 위험이 커서다. 윈도우도 같은 시군구보다 짧은 adjacent-window-hours 를 쓴다.
     * 비사고성(폭염·호우 등 템플릿형)은 시군구 간 다리(blob)가 생기므로 대상이 아니다.
     * 후보 이벤트에도 LLM 폴백과 같은 두 가드(사고성 유형, 화재 72h)를 적용한다 — 별개 화재 오병합 방지와 같은 이유.
     *
     * @return 인접 이벤트에 병합했으면 true
     */
    private boolean tryMergeAdjacent(DisasterAlert alert, String embeddingText, String[] regionCodes,
                                     double mergeMaxDistance) {
        if (!isAccidentType(alert.getDisasterType())) {
            return false;
        }
        LocalDateTime adjacentSince = alert.getCreatedAt().minusHours(adjacentWindowHours);
        List<Object[]> adjacent = disasterEventRepository.findTopAdjacentCandidates(
                embeddingText, sigunguPrefixes(regionCodes), adjacentSince);
        log.info("clusterNewAlert: alertId={} 인접 시군구 후보 {}건 (윈도우 {}h)",
                alert.getId(), adjacent.size(), adjacentWindowHours);
        logCandidates(alert.getId(), adjacent);

        // 거리 오름차순이므로 임계 이내의 첫 후보가 가장 가까운 이벤트.
        for (Object[] row : adjacent) {
            Long eventId = ((Number) row[0]).longValue();
            double dist = ((Number) row[1]).doubleValue();
            if (dist > mergeMaxDistance) {
                continue;
            }
            // 가드 실패 후보는 건너뛰고 다음(더 먼) 후보를 본다.
            DisasterEvent ev = disasterEventRepository.findById(eventId).orElse(null);
            if (allowsAccidentMerge(alert, ev) && withinAdjacentEventAge(alert, ev)) {
                mergeIntoExisting(eventId, alert, 1.0 - dist, MergeMethod.EMBEDDING);
                return true;
            }
        }
        return false;
    }

    /**
     * 인접 병합 전용 이벤트 나이 상한 — 후보 이벤트 첫 알림부터 새 알림까지 adjacent-max-event-age-hours 이내(경계 포함).
     * 인접 병합이 last_alert_at 윈도우를 계속 밀어 안내문이 44일·7개 시군구로 연쇄 병합되던 문제(평가 DB 실측) 차단.
     * 첫 알림 시각을 모르면 나이를 증명할 수 없으므로 거부한다. 같은 시군구 경로(tryLlmFallback)에는 적용하지 않는다.
     */
    private boolean withinAdjacentEventAge(DisasterAlert alert, DisasterEvent ev) {
        if (ev == null || ev.getFirstAlertAt() == null) {
            return false;
        }
        return !alert.getCreatedAt().isAfter(ev.getFirstAlertAt().plusHours(adjacentMaxEventAgeHours));
    }

    /**
     * borderline 후보(임베딩 임계 미만, ceil 이내)를 LLM 으로 동일 사건 판정해 머지 시도.
     *
     * <p>게이트: 사고성 유형(화이트리스트) + 동물·비정형 아님
     * (동물은 cross-region 단계, 인물은 {@link #clusterPerson} 앞단에서 이미 분기). 후보가 없거나
     * LLM 이 NONE(보수적)이면 false → 신규 이벤트로 진행.
     *
     * @return LLM 이 같은 사건으로 판정해 머지하면 true
     */
    private boolean tryLlmFallback(DisasterAlert alert, List<Object[]> candidates, double mergeMaxDistance) {
        if (!isAccidentType(alert.getDisasterType()) || isAnimalCase(alert.getMessage())) {
            return false;
        }

        // borderline 후보: 임베딩 임계는 못 넘었지만(>mergeMaxDistance) ceil 이내인 같은 지역 후보.
        // candidates 는 거리 ASC 정렬이라 가까운 후보부터 담긴다.
        List<Long> borderlineIds = new ArrayList<>();
        for (Object[] row : candidates) {
            double dist = ((Number) row[1]).doubleValue();
            if (dist > mergeMaxDistance && dist <= llmFallbackDistanceCeil) {
                borderlineIds.add(((Number) row[0]).longValue());
            }
        }
        if (borderlineIds.isEmpty()) {
            return false;
        }

        // 후보 이벤트도 사고성 유형(seed 기준)이어야 머지 — 화이트리스트 알림이 기상특보(호우/한파/풍랑)
        // 이벤트에 빨려들어가는 과병합 차단. (예: '비 예보' 호우 이벤트에 '난방기 화재' 알림이 흡수되던 케이스)
        Map<Long, String> repMessages = representativeMessages(borderlineIds);
        List<EventLLMDecisionService.Candidate> cands = new ArrayList<>(borderlineIds.size());
        for (Long id : borderlineIds) {
            if (!allowsAccidentMerge(alert, disasterEventRepository.findById(id).orElse(null))) {
                continue;
            }
            cands.add(new EventLLMDecisionService.Candidate(id, repMessages.getOrDefault(id, "")));
        }
        if (cands.isEmpty()) {
            return false;
        }

        Integer pick = llmDecisionService.pickSameGeneralIncident(alert.getMessage(), cands);
        if (pick == null) {
            return false;
        }
        Long targetEventId = cands.get(pick).eventId();
        log.info("clusterNewAlert(LLM폴백): alertId={} → event={} 같은 사건 판정 머지 (type={}, 후보 {}건)",
                alert.getId(), targetEventId, alert.getDisasterType(), cands.size());
        mergeIntoExisting(targetEventId, alert, null, MergeMethod.LLM_FALLBACK);
        return true;
    }

    /**
     * 후보 이벤트가 사고성 유형 병합 대상인지 — LLM 폴백과 인접 병합이 공유하는 두 가드.
     * (1) 후보 이벤트도 사고성 유형이어야 한다(기상특보 이벤트에 화재 알림이 흡수되는 과병합 차단).
     * (2) 화재는 별개 화재를 합치는 오병합이 많아 이벤트 마지막 알림 72h 초과 시 제외(운영 데이터 분석 2026-10).
     */
    private boolean allowsAccidentMerge(DisasterAlert alert, DisasterEvent ev) {
        return ev != null
                && isAccidentType(ev.getPrimaryDisasterType())
                && DisasterCooldown.allowsLlmFallbackMerge(alert.getDisasterType(),
                        ev.getFirstAlertAt(), ev.getLastAlertAt(), alert.getCreatedAt());
    }

    /** 사고성 유형 화이트리스트 매칭 (LLM 폴백 적용 대상). */
    private boolean isAccidentType(String type) {
        if (type == null) {
            return false;
        }
        if (accidentTypes == null) {
            accidentTypes = Arrays.stream(accidentTypesCsv.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        }
        return accidentTypes.contains(type.trim());
    }

    /** 동물·비정형(탈출/출몰/멧돼지 등) 알림 여부 — LLM 폴백 제외(cross-region 담당). */
    private boolean isAnimalCase(String msg) {
        if (msg == null) {
            return false;
        }
        if (animalPattern == null) {
            animalPattern = Pattern.compile(animalKeywords);
        }
        return animalPattern.matcher(msg).find();
    }

    /** 후보 이벤트들의 대표(seed) 본문 조회 → {eventId: message}. LLM 프롬프트용. */
    private Map<Long, String> representativeMessages(List<Long> eventIds) {
        Map<Long, String> map = new HashMap<>();
        for (Object[] row : eventAlertMappingRepository.findRepresentativeMessages(eventIds)) {
            map.put(((Number) row[0]).longValue(), (String) row[1]);
        }
        return map;
    }

    /**
     * 실종 인물 신원 클러스터링 — 같은 이름+나이+키 이벤트에 머지(시군구·임베딩 무관), 없으면 신규.
     *
     * <p>실종 경찰문자는 "OOO씨(성별,N세) …cm" 정형이라 동일인을 결정적으로 식별할 수 있다.
     * 임베딩+지역으로 묶으면 (a) 지역 옮긴 같은 사람을 못 묶고 (b) 같은 시군구의 다른 실종자를
     * 템플릿 유사도로 잘못 묶는다. 그래서 인물은 이 경로로만 처리(LLM·임베딩 미사용, recluster 공짜).
     *
     * @return 인물로 처리하면 true, 아니면(일반 재난·동물·비정형) false → 임베딩 경로로.
     */
    private boolean clusterPerson(DisasterAlert alert) {
        if (!"기타".equals(alert.getDisasterType()) || !MissingPersonIdentity.isPerson(alert.getMessage())) {
            return false;
        }
        String name = MissingPersonIdentity.name(alert.getMessage());
        String age = MissingPersonIdentity.age(alert.getMessage());
        String height = MissingPersonIdentity.height(alert.getMessage());
        String key = name + " " + age + "세" + (height == null ? "" : " " + height + "cm");

        LocalDateTime since = alert.getCreatedAt().minusHours(personWindowHours);
        // 같은 이름+나이+키 이벤트 (시군구 무관). selfEventId=-1 → 제외 없음(신규 알림), top-1.
        List<Object[]> rows = disasterEventRepository.findCrossRegionCandidatesByPerson(
                name, age, height, since, -1L, 1);
        if (!rows.isEmpty()) {
            Long targetEventId = ((Number) rows.get(0)[0]).longValue();
            mergeIntoExisting(targetEventId, alert, null, MergeMethod.IDENTITY);
            log.info("clusterNewAlert(인물): alertId={} → event={} 동일인 머지({})", alert.getId(), targetEventId, key);
        } else {
            String[] codes = extractRegionCodes(alert);
            createNewEvent(alert, codes.length > 0 ? codes[0] : null, extractFirstRegionName(alert), false);
            log.info("clusterNewAlert(인물): alertId={} → 신규 이벤트({})", alert.getId(), key);
        }
        return true;
    }

    /**
     * 전국 통합 유형 클러스터링 — 태풍처럼 본질적으로 지역 무관한 단일 사건 유형을, 윈도우 안의
     * 같은 유형 전국 broadcast 이벤트 하나에 머지(없으면 신규). 지역·임베딩을 보지 않는다.
     *
     * <p>태풍 알림은 대부분 시군구 단위로 따로 발송돼({@code sigungu_span=1}) {@link #clusterBroadcast}
     * 의 span 게이트({@code maxRegionSpan})를 못 넘고, local 임베딩 경로의 지역 hard 필터에 막혀
     * 시군구마다 별도 이벤트가 됐다. 유형 자체를 키로 삼아 이 파편화를 막는다. 인물 IDENTITY 와 같은
     * 철학(유형이 신원). 같은 윈도우(기본 7일) 안의 연속 발송은 같은 태풍으로 간주.
     *
     * @return 전국 통합 유형으로 처리하면 true, 아니면(일반 유형) false → 임베딩 경로로.
     */
    private boolean clusterGlobalType(DisasterAlert alert) {
        if (!isGlobalType(alert.getDisasterType())) {
            return false;
        }
        String type = alert.getDisasterType();
        LocalDateTime since = alert.getCreatedAt().minusHours(globalWindowHours);
        // 전국 broadcast(region_code=null) 이벤트만 키로 — broadcast 격리 규칙과 일관(local 과 안 섞임).
        Optional<DisasterEvent> target = disasterEventRepository
                .findFirstByBroadcastTrueAndPrimaryDisasterTypeAndPrimaryRegionCodeIsNullAndLastAlertAtAfterOrderByLastAlertAtDesc(
                        type, since);
        if (target.isPresent()) {
            mergeIntoExisting(target.get().getId(), alert, null, MergeMethod.GLOBAL_TYPE);
            upgradeGlobalTitleIfNamed(target.get().getId(), type, alert.getMessage());
            log.info("clusterNewAlert(전국통합): alertId={} → event={} 유형 머지({})",
                    alert.getId(), target.get().getId(), type);
        } else {
            createNewEvent(alert, null, "전국", true);
            log.info("clusterNewAlert(전국통합): alertId={} → 신규 전국 이벤트({})", alert.getId(), type);
        }
        return true;
    }

    /** 전국 통합 유형(태풍 등) 화이트리스트 매칭 — 지역·임베딩 무관 단일 이벤트 대상. */
    private boolean isGlobalType(String type) {
        if (type == null) {
            return false;
        }
        if (globalTypes == null) {
            globalTypes = Arrays.stream(globalTypesCsv.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        }
        return globalTypes.contains(type.trim());
    }

    /**
     * 전국 통합 태풍 이벤트 제목 승급 — 머지된 알림에 태풍명이 있고 이벤트 제목이 아직 기본("전국 태풍")
     * 이면 "태풍 {이름}"으로 올린다. 큰 태풍 이벤트의 첫 알림이 "태풍 북상 예상"처럼 이름 없이 시작해도
     * (예: 종다리) 이름을 가진 후속 알림에서 제목이 채워진다. 이미 이름이 붙었으면 갱신 쿼리가 no-op.
     */
    private void upgradeGlobalTitleIfNamed(Long eventId, String type, String message) {
        String name = DisasterEvent.extractTyphoonName(type, message);
        if (name != null) {
            disasterEventRepository.updateTitleIfEquals(
                    eventId, "태풍 " + name, DisasterEvent.genericGlobalTitle(type));
        }
    }

    /**
     * 지역앵커 유형 클러스터링 — 산불·산사태·홍수처럼 "유형+지역이 곧 사건"인 이산 재난을, 같은 시군구·
     * 같은 유형·유형별 윈도우 안의 기존 이벤트에 머지(없으면 신규). 지역·유형은 보되 임베딩·LLM 은 안 본다.
     *
     * <p>이 유형들은 한 사건(산불/호우 episode)이 같은 시군구에서 며칠 burst 로 쏟아지며 본문 텍스트가
     * 갈려(코사인&lt;0.85) local 임베딩 경로가 한 사건을 수십 개로 파편화했다(예: 산청 산불 16조각). 유형
     * 자체를 키로 삼아 막는다 — 인물 IDENTITY·태풍 GLOBAL_TYPE 과 같은 철학이되, 태풍이 지역을 버리는
     * 것과 달리 <b>시군구 스코프</b>라 전국 blob 이 안 생긴다. episode 간 갭이 윈도우보다 커서(실측: 산불
     * 재발화 7~30일, 호우성 같은 날 burst 후 9~25일) 서로 다른 episode 는 자연 분리된다.
     *
     * <p>윈도우는 유형별 — 산불 14일(재발화 길다), 산사태·홍수 7일(같은 날 burst). 광역 broadcast
     * (span&gt;{@code maxRegionSpan})·지역 없음은 여기서 처리하지 않고 false → 기존 broadcast/임베딩
     * 경로로 넘긴다(전국 산불이 시군구로 안 쪼개지게 격리 유지).
     *
     * @return 지역앵커 유형으로 처리하면 true, 아니면(일반 유형·광역·지역없음) false → 임베딩 경로로.
     */
    private boolean clusterRegionalType(DisasterAlert alert) {
        Integer windowHours = regionalTypeWindowHours(alert.getDisasterType());
        if (windowHours == null) {
            return false;
        }
        String[] regionCodes = extractRegionCodes(alert);
        if (regionCodes.length == 0) {
            return false;  // 지역 없으면 시군구 키 못 만듦 → 임베딩 경로(신규 생성)로
        }
        // 광역 broadcast(다수 시군구)는 여기서 안 잡고 기존 broadcast 경로가 (시도+유형/전국) 키로 격리.
        // 전국 산불이 시군구마다 쪼개지지 않게 — span 게이트는 local 경로와 동일 기준.
        if (distinctSigunguCount(regionCodes) > maxRegionSpan) {
            return false;
        }

        // 안내성 분리(산불): 건조특보·소각금지·예방캠페인 등 실제 화재가 아닌 안내 알림은 사건 머지에
        //   태우지 않고 시군구별 롤링 안내 이벤트로 모은다. 안내성이 14일 윈도우로 시즌 내내 체이닝돼
        //   사건 버킷을 blob(span 130일+)으로 만들고 소수 실사건을 오염시키던 것을 발생 지점에서 차단.
        if (isAdvisorySplitType(alert.getDisasterType())
                && AdvisoryClassifier.isAdvisory(alert.getDisasterType(), alert.getMessage(), alert.getEmergencyLevel())) {
            clusterAdvisory(alert, regionCodes, windowHours);
            return true;
        }

        String[] sigungu = sigunguPrefixes(regionCodes);
        LocalDateTime since = alert.getCreatedAt().minusHours(windowHours);
        Optional<Long> target = disasterEventRepository.findRegionalTypeMergeTarget(
                alert.getDisasterType(), sigungu, since);
        if (target.isPresent()) {
            mergeIntoExisting(target.get(), alert, null, MergeMethod.REGIONAL_TYPE);
            log.info("clusterNewAlert(지역앵커): alertId={} → event={} 유형+시군구 머지({}, window={}h)",
                    alert.getId(), target.get(), alert.getDisasterType(), windowHours);
        } else {
            createNewEvent(alert, regionCodes[0], extractFirstRegionName(alert), false);
            log.info("clusterNewAlert(지역앵커): alertId={} → 신규 이벤트({}, window={}h)",
                    alert.getId(), alert.getDisasterType(), windowHours);
        }
        return true;
    }

    /**
     * 에피소드형 유형(폭염·한파·대설·강풍·가축질병) 클러스터링 — "유형 + 시군구"가 곧 하나의 이벤트.
     * 같은 시군구에서 같은 유형의 발령이 끊김 없이 이어지는 동안은 문장 유사도와 무관하게 합친다
     * (연쇄 병합은 의도). 끊김 기준은 마지막 알림 이후 {@code episodic-window-hours}. 본문은 보지 않는다.
     *
     * <p>시군구가 정확히 1개면 위 앵커링, 2개 이상(다지역)이면 지역 이벤트 후보에서 빼고 곧바로 광역
     * (broadcast) 경로로 보낸다(span 이 maxRegionSpan 이하든 초과든 동일). 다지역 알림을 임베딩 경로에 두면
     * footprint 교집합 머지가 서로 다른 시군구의 지역 이벤트를 이어 붙여(시군구 간 다리) 폭염 이벤트 하나가
     * 43개 시군구·7개 시도·612건까지 커졌기 때문이다. 사고성·비대상 유형은 사고 전파를 허용해야 하므로
     * 이 분기를 타지 않는다.
     *
     * @return 에피소드형으로 처리하면 true (지역코드가 없으면 false)
     */
    private boolean clusterEpisodicType(DisasterAlert alert) {
        String type = alert.getDisasterType();
        if (!isEpisodicType(type)) {
            return false;
        }
        String[] regionCodes = extractRegionCodes(alert);
        if (regionCodes.length == 0) {
            return false;
        }
        int sigunguSpan = distinctSigunguCount(regionCodes);
        if (sigunguSpan > 1) {
            clusterBroadcast(alert, regionCodes, sigunguSpan);
            return true;
        }
        LocalDateTime since = alert.getCreatedAt().minusHours(episodicWindowHours);
        Optional<Long> target = disasterEventRepository.findRegionalTypeMergeTarget(
                type, sigunguPrefixes(regionCodes), since);
        if (target.isPresent()) {
            mergeIntoExisting(target.get(), alert, null, MergeMethod.REGIONAL_TYPE);
            log.info("clusterNewAlert(에피소드형): alertId={} → event={} 유형+시군구 머지({}, window={}h)",
                    alert.getId(), target.get(), type, episodicWindowHours);
        } else {
            createNewEvent(alert, regionCodes[0], extractFirstRegionName(alert), false);
            log.info("clusterNewAlert(에피소드형): alertId={} → 신규 이벤트({}, window={}h)",
                    alert.getId(), type, episodicWindowHours);
        }
        return true;
    }

    /**
     * 안내성 유형(advisory-split-types, 산불 제외 — 산불은 clusterRegionalType 에서 처리)의 안내 알림을
     * 시군구별 안내 이벤트로 모은다. 평가 DB 에서 "기타" 물놀이 안내문이 인접 시군구 병합으로 이어 붙어
     * 7개 시군구·44일짜리 이벤트가 된 것이 실측됐다. 안내문은 사건이 아니므로 임베딩·후보 조회·LLM 을 거치지
     * 않고 시군구별 안내 이벤트(is_advisory=true)로 직접 모은다. 이웃 시군구 병합은 후보 검색이
     * is_advisory=false 만 보기 때문에 안내 이벤트가 사건 후보가 되지 않아 차단된다.
     *
     * <p>산불은 지역앵커 유형이라 clusterRegionalType 이 유형별 윈도우로 처리하므로 여기서 제외한다.
     * 다지역(시군구 2개 이상) 안내문은 시군구 사이 다리를 만들지 않도록 광역 경로로 보낸다.
     *
     * @return 안내성으로 처리하면 true (대상 아님/지역코드 없음이면 false)
     */
    private boolean clusterAdvisoryType(DisasterAlert alert) {
        String type = alert.getDisasterType();
        if (!isAdvisorySplitType(type) || regionalTypeWindowHours(type) != null
                || !AdvisoryClassifier.isAdvisory(type, alert.getMessage(), alert.getEmergencyLevel())) {
            return false;
        }
        String[] regionCodes = extractRegionCodes(alert);
        if (regionCodes.length == 0) {
            return false;
        }
        int sigunguSpan = distinctSigunguCount(regionCodes);
        if (sigunguSpan > 1) {
            clusterAdvisoryBroadcast(alert, regionCodes);
        } else {
            clusterAdvisory(alert, regionCodes, advisoryWindowHours);
        }
        return true;
    }

    /**
     * 다지역(시군구 2개 이상) 안내문을 시도(또는 전국) + 유형 키의 안내 광역 이벤트로 모은다.
     * 유형 '기타' 는 V7 에서 uninformative 라 {@link #clusterBroadcast} 가 병합 없이 매번 단발 광역 이벤트를
     * 만들어, 다지역 물놀이 안내마다 이벤트가 늘었다(평가 DB 51개). 안내는 본문이 달라도 같은 묶음이므로
     * 안내 윈도우 안에서 병합하되, 일반 '기타' 광역 이벤트와 섞이지 않게 is_advisory=true 인 것만 대상으로 삼는다.
     */
    private void clusterAdvisoryBroadcast(DisasterAlert alert, String[] regionCodes) {
        String type = alert.getDisasterType();
        LocalDateTime since = alert.getCreatedAt().minusHours(advisoryWindowHours);
        boolean nationwide = distinctSidoCount(regionCodes) >= nationwideSidoSpan;
        String sido = nationwide ? null : dominantSidoPrefix(regionCodes);
        Optional<DisasterEvent> target = nationwide
                ? disasterEventRepository.findAdvisoryBroadcastNationwide(type, since)
                : disasterEventRepository.findAdvisoryBroadcastBySido(type, sido, since);
        if (target.isPresent()) {
            mergeIntoExisting(target.get().getId(), alert, null, MergeMethod.BROADCAST);
            log.info("clusterNewAlert(안내 광역): alertId={} → event={} 머지({} {})",
                    alert.getId(), target.get().getId(), nationwide ? "전국" : sido, type);
            return;
        }
        DisasterEvent event = DisasterEvent.createAdvisory(type,
                nationwide ? null : firstCodeOfSido(regionCodes, sido),
                nationwide ? "전국" : sidoNameForPrefix(alert, sido),
                alert.getCreatedAt(), true);
        DisasterEvent saved = disasterEventRepository.save(event);
        eventAlertMappingRepository.save(EventAlertMapping.of(saved.getId(), alert.getId(), 1, null, MergeMethod.SEED));
        eventPublisher.publishEvent(new AlertClusteredEvent(saved.getId(), alert.getId()));
        log.info("clusterNewAlert(안내 광역): alertId={} → 신규 안내 광역 event={} (title='{}')",
                alert.getId(), saved.getId(), saved.getEventTitle());
    }

    /** 에피소드형 유형 매칭. 유형 null 이거나 목록이 비어 있으면 적용하지 않는다. */
    private boolean isEpisodicType(String type) {
        if (type == null) {
            return false;
        }
        if (episodicTypes == null) {
            episodicTypes = parseCsvSet(episodicTypesCsv);
        }
        return episodicTypes.contains(type.trim());
    }

    /**
     * 안내성 알림을 시군구별 롤링 안내 이벤트에 머지(없으면 신규). 같은 유형·같은 시군구·윈도우 안의
     * {@code is_advisory=true} 이벤트만 대상이라 사건 버킷과 섞이지 않는다. 임베딩·LLM 안 봄.
     */
    private void clusterAdvisory(DisasterAlert alert, String[] regionCodes, int windowHours) {
        String[] sigungu = sigunguPrefixes(regionCodes);
        LocalDateTime since = alert.getCreatedAt().minusHours(windowHours);
        Optional<Long> target = disasterEventRepository.findAdvisoryMergeTarget(
                alert.getDisasterType(), sigungu, since);
        if (target.isPresent()) {
            mergeIntoExisting(target.get(), alert, null, MergeMethod.ADVISORY);
            log.info("clusterNewAlert(안내성): alertId={} → event={} 시군구 안내 머지({}, window={}h)",
                    alert.getId(), target.get(), alert.getDisasterType(), windowHours);
        } else {
            DisasterEvent event = DisasterEvent.createAdvisory(
                    alert.getDisasterType(), regionCodes[0], extractFirstRegionName(alert), alert.getCreatedAt());
            DisasterEvent saved = disasterEventRepository.save(event);
            eventAlertMappingRepository.save(EventAlertMapping.of(saved.getId(), alert.getId(), 1, null, MergeMethod.SEED));
            eventPublisher.publishEvent(new AlertClusteredEvent(saved.getId(), alert.getId()));
            log.info("clusterNewAlert(안내성): alertId={} → 신규 안내 event={}({}, window={}h, title='{}')",
                    alert.getId(), saved.getId(), alert.getDisasterType(), windowHours, saved.getEventTitle());
        }
    }

    /** 안내성 분리 적용 유형(산불 등) 매칭. regionalTypesCsv 의 부분집합. */
    private boolean isAdvisorySplitType(String type) {
        if (type == null) {
            return false;
        }
        if (advisorySplitTypes == null) {
            advisorySplitTypes = parseCsvSet(advisorySplitTypesCsv);
        }
        return advisorySplitTypes.contains(type.trim());
    }

    /**
     * 임베딩 병합 거리 상한. 에피소드형 유형(폭염·한파 등)은 같은 행동요령을 표현만 바꿔 되풀이해 거리가
     * 0.15 를 넘기 쉬우므로 완화 상한을, 그 외는 기본 상한을 쓴다. LLM 폴백(borderline 구간 시작점)에도
     * 같은 값을 넘겨 "머지 불가 구간"의 기준을 알림 단위로 일관되게 유지한다.
     */
    private double mergeMaxDistanceFor(String type) {
        if (isRelaxedThresholdType(type)) {
            return 1.0 - relaxedSimilarityThreshold;
        }
        return 1.0 - similarityThreshold;
    }

    /** 병합 임계값 완화 대상 유형 매칭. 유형 null 이거나 목록이 비어 있으면 완화하지 않는다. */
    private boolean isRelaxedThresholdType(String type) {
        if (type == null) {
            return false;
        }
        if (relaxedThresholdTypes == null) {
            relaxedThresholdTypes = parseCsvSet(relaxedThresholdTypesCsv);
        }
        return relaxedThresholdTypes.contains(type.trim());
    }

    /** 쉼표 구분 목록 → 집합. null/빈 값은 빈 집합(설정이 없으면 해당 기능을 적용하지 않는다). */
    private static Set<String> parseCsvSet(String csv) {
        if (csv == null) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * 지역앵커 유형이면 유형별 머지 윈도우(시간), 아니면 null. CSV {@code "유형:시간,유형:시간"} 1회 파싱.
     * 잘못된 토큰은 건너뛴다(부분 실패가 전체를 막지 않게).
     */
    private Integer regionalTypeWindowHours(String type) {
        if (type == null) {
            return null;
        }
        if (regionalTypeWindows == null) {
            Map<String, Integer> m = new HashMap<>();
            for (String pair : regionalTypesCsv.split(",")) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    try {
                        m.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
                    } catch (NumberFormatException e) {
                        log.warn("regional-types 파싱 실패: '{}' — 건너뜀", pair);
                    }
                }
            }
            regionalTypeWindows = m;
        }
        return regionalTypeWindows.get(type.trim());
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

    /**
     * 기존 이벤트에 알림 머지. {@code similarity} 는 합류 시점 코사인 유사도(1 - dist)로,
     * event_alert_mapping 에 저장한다. broadcast 머지(임베딩 미사용)는 null 을 넘긴다.
     * {@code method} 는 합류 방식 표식(EMBEDDING/BROADCAST).
     */
    private void mergeIntoExisting(Long eventId, DisasterAlert alert, Double similarity, MergeMethod method) {
        int nextSeq = eventAlertMappingRepository.countByEventId(eventId) + 1;
        eventAlertMappingRepository.save(EventAlertMapping.of(eventId, alert.getId(), nextSeq, similarity, method));
        disasterEventRepository.incrementOnMerge(eventId, alert.getCreatedAt());
        // 위험도 모듈 트리거 (커밋 후 @TransactionalEventListener 가 수신)
        eventPublisher.publishEvent(new AlertClusteredEvent(eventId, alert.getId()));
        log.info("clusterNewAlert: alertId={} → event={} 머지 (method={}, similarity={}, seq={})",
                alert.getId(), eventId, method, similarity, nextSeq);
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
        eventAlertMappingRepository.save(EventAlertMapping.of(saved.getId(), alert.getId(), 1, null, MergeMethod.SEED));
        // 위험도 모듈 트리거 (커밋 후 @TransactionalEventListener 가 수신)
        eventPublisher.publishEvent(new AlertClusteredEvent(saved.getId(), alert.getId()));
        log.info("clusterNewAlert: alertId={} → 신규 event={} (broadcast={}, title='{}')",
                alert.getId(), saved.getId(), broadcast, saved.getEventTitle());
    }

    /**
     * cross-region 병합 primitive — source 이벤트의 모든 매핑을 target 으로 옮기고 source 를 삭제.
     *
     * <p>실종자·탈출 동물이 시군구를 옮겨다녀 따로 만들어진 이벤트(대개 단일 알림)를, LLM 이 같은
     * 사건으로 판정했을 때 하나로 합친다. 옮겨진 매핑은 sequence_no 를 이어붙이고 merge_method 를
     * 지정(LLM)한다. 합친 뒤 target 의 alert_count/first/last_alert_at 을 재계산.
     */
    @Transactional
    public void mergeEvents(Long sourceEventId, Long targetEventId, MergeMethod method) {
        if (sourceEventId == null || targetEventId == null || sourceEventId.equals(targetEventId)) {
            return;
        }
        int offset = eventAlertMappingRepository.countByEventId(targetEventId);
        int moved = eventAlertMappingRepository.reassignToEvent(sourceEventId, targetEventId, offset, method.name());
        disasterEventRepository.recomputeAggregates(targetEventId);
        disasterEventRepository.deleteById(sourceEventId);
        log.info("cross-region 머지: event {} → {} (method={}, moved={}, offset={})",
                sourceEventId, targetEventId, method, moved, offset);
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
                    mergeIntoExisting(target.get().getId(), alert, null, MergeMethod.BROADCAST);
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
                mergeIntoExisting(target.get().getId(), alert, null, MergeMethod.BROADCAST);
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

    /**
     * 지역 코드들의 distinct 시군구 prefix(앞 5자) — local 후보 지역 hard 필터를 읍면동이 아닌 시군구 단위로 맞춤.
     * 같은 사건이라도 한 알림은 군레벨(4886000000), 다른 알림은 읍면동(4886036000)으로 태깅돼 full code 정확
     * 일치로는 교집합이 0이 되던 과소병합(산청 산불 등)을 차단. 임베딩≥0.85 + 시간 윈도우는 그대로라 같은
     * 시군구·유사본문·근접시각만 묶인다.
     */
    private String[] sigunguPrefixes(String[] regionCodes) {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        for (String code : regionCodes) {
            if (code != null && code.length() >= 5) set.add(code.substring(0, 5));
        }
        return set.toArray(new String[0]);
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
