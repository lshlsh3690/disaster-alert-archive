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
import java.util.regex.Matcher;
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

    /** 동물·비정형 경로 라우팅용 — 이 키워드가 있고 인물(이름+나이) 추출이 안 되면 임베딩+LLM. */
    @Value("${clustering.cross-region.animal-keywords:탈출|출몰|멧돼지|들개|늑대}")
    private String animalKeywords;

    private Pattern moverPattern;
    private Pattern animalPattern;

    private Pattern moverPattern() {
        if (moverPattern == null) {
            moverPattern = Pattern.compile(moverKeywords);
        }
        return moverPattern;
    }

    private Pattern animalPattern() {
        if (animalPattern == null) {
            animalPattern = Pattern.compile(animalKeywords);
        }
        return animalPattern;
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
        LocalDateTime since = alert.getCreatedAt().minusHours(windowHours);

        String name = extractName(alert.getMessage());
        String age = extractAge(alert.getMessage());
        if (name != null && age != null) {
            // 인물: 이름+나이+키 결정적 매칭 (LLM 미사용)
            linkPerson(alert, selfEventId, since, name, age);
        } else if (animalPattern().matcher(alert.getMessage()).find()) {
            // 동물·비정형: 임베딩 top-K + LLM 판정
            linkAnimal(alert, selfEventId, since);
        }
        // 그 외(인물인데 이름 추출 실패 등) → skip. 임베딩 폴백으로 다른 사람과 섞이는 blob 방지.
    }

    /**
     * 인물 cross-region — <b>결정적 매칭</b>(이름+나이+키 일치). LLM 미사용.
     *
     * <p>경찰 실종문자는 "OOO씨(성별,N세) …cm" 로 정형화돼 있어, 같은 이름+나이+키면 거의 확실히 같은
     * 사람이다. 후보(쿼리가 이미 이름+나이+키 매칭)를 최근순으로 받아 span-cap 통과하는 첫 이벤트에 병합.
     * 동명이인은 나이/키가 달라 후보에서 빠지므로 다른 사람이 섞이지 않는다.
     */
    private void linkPerson(DisasterAlert alert, Long selfEventId, LocalDateTime since, String name, String age) {
        String height = extractHeight(alert.getMessage());
        List<Object[]> rows = disasterEventRepository.findCrossRegionCandidatesByPerson(
                name, age, height, since, selfEventId, topK);
        for (Object[] row : rows) {
            Long targetEventId = ((Number) row[0]).longValue();
            if (exceedsSpanCap(targetEventId, selfEventId)) {
                continue;
            }
            log.info("linkCrossRegion(인물): alertId={} event {} → {} 동일인({} {}세{}) — 병합",
                    alert.getId(), selfEventId, targetEventId, name, age,
                    height == null ? "" : " " + height + "cm");
            eventClusteringService.mergeEvents(selfEventId, targetEventId, MergeMethod.IDENTITY);
            return;
        }
    }

    /**
     * 동물·비정형 cross-region — 정형 키가 없어 임베딩 top-K 후보 + LLM 판정.
     * 탈출·출몰·늑대 등 건수가 적고 본문이 자유서술이라 LLM 이 유일한 수단.
     */
    private void linkAnimal(DisasterAlert alert, Long selfEventId, LocalDateTime since) {
        String embeddingText = alertEmbeddingRepository.findEmbeddingText(alert.getId());
        if (embeddingText == null) {
            return;
        }
        List<Object[]> rows = disasterEventRepository.findCrossRegionCandidates(
                embeddingText, since, selfEventId, moverKeywords, 1.0 - similarityFloor, topK);
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

    private static final Pattern PERSON_NAME = Pattern.compile("([가-힣]{2,4})씨");
    private static final Pattern PERSON_AGE = Pattern.compile("(\\d{1,3})\\s*세");
    private static final Pattern PERSON_HEIGHT = Pattern.compile("(\\d{2,3})\\s*cm");

    /** "OOO씨" 에서 이름 추출 (실종 경찰문자 정형). 없으면 null(동물·비정형). */
    private static String extractName(String message) {
        if (message == null) {
            return null;
        }
        Matcher m = PERSON_NAME.matcher(message);
        return m.find() ? m.group(1) : null;
    }

    /** "N세" 에서 나이 추출. 없으면 null. */
    private static String extractAge(String message) {
        if (message == null) {
            return null;
        }
        Matcher m = PERSON_AGE.matcher(message);
        return m.find() ? m.group(1) : null;
    }

    /** "NNNcm" 에서 키 추출 (동일인 확정 키). 없으면 null → 이름+나이만으로 매칭. */
    private static String extractHeight(String message) {
        if (message == null) {
            return null;
        }
        Matcher m = PERSON_HEIGHT.matcher(message);
        return m.find() ? m.group(1) : null;
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
