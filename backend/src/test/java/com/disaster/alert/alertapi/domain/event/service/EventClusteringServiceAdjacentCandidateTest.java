package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import com.disaster.alert.alertapi.domain.event.model.MergeMethod;
import com.disaster.alert.alertapi.domain.event.repository.AlertEmbeddingRepository;
import com.disaster.alert.alertapi.domain.event.repository.DisasterEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 사고성 유형의 인접 시군구 후보 확장 검증 — 같은 시군구 임계값 병합 실패 시, 사고성 유형이면
 * 인접 시군구 후보(findTopAdjacentCandidates)를 임계값만으로 병합한다. 인접 후보는 LLM 폴백 대상이 아니다.
 *
 * <p>순서: 같은 시군구 임계값 병합 → 인접 시군구 임계값 병합 → LLM 폴백(같은 시군구 borderline) → 신규 이벤트.
 * 순수 Mockito 단위 테스트(DB·Spring 컨텍스트 없음).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventClusteringServiceAdjacentCandidateTest {

    private static final String REGION = "4480000000";
    private static final long SAME_EVENT_ID = 55L;
    private static final long ADJACENT_EVENT_ID = 77L;

    private static final String FIRE = "홍성군 OO동 공장 화재 발생, 인근 주민은 대피 바랍니다.";
    private static final String RAIN = "호우주의보 발효 중, 하천 접근을 자제 바랍니다.[홍성군]";

    @Mock EmbeddingModel embeddingModel;
    @Mock DisasterAlertRepository disasterAlertRepository;
    @Mock DisasterEventRepository disasterEventRepository;
    @Mock AlertEmbeddingRepository alertEmbeddingRepository;
    @Mock EventLLMDecisionService llmDecisionService;

    EventClusteringService service;
    LocalDateTime now = LocalDateTime.of(2026, 7, 20, 12, 0);

    /** 후보로 등록한 이벤트들(id → 객체). 병합은 이 객체를 직접 바꾸므로 갱신 여부를 값으로 확인한다. */
    private final java.util.Map<Long, DisasterEvent> events = new java.util.HashMap<>();

    @BeforeEach
    void setUp() {
        service = new EventClusteringService(embeddingModel, disasterAlertRepository, disasterEventRepository,
                alertEmbeddingRepository, llmDecisionService);
        ReflectionTestUtils.setField(service, "candidateWindowHours", 168);
        ReflectionTestUtils.setField(service, "maxRegionSpan", 10);
        ReflectionTestUtils.setField(service, "nationwideSidoSpan", 8);
        ReflectionTestUtils.setField(service, "personWindowHours", 336);
        ReflectionTestUtils.setField(service, "llmFallbackDistanceCeil", 0.40);
        ReflectionTestUtils.setField(service, "accidentTypesCsv", "기타,화재,산불,붕괴");
        ReflectionTestUtils.setField(service, "animalKeywords", "탈출|출몰|멧돼지|들개|늑대");
        ReflectionTestUtils.setField(service, "globalTypesCsv", "태풍");
        ReflectionTestUtils.setField(service, "globalWindowHours", 168);
        ReflectionTestUtils.setField(service, "regionalTypesCsv", "산불:336,산사태:168,홍수:168");
        ReflectionTestUtils.setField(service, "sustainedTypesCsv", "");
        ReflectionTestUtils.setField(service, "adjacentWindowHours", 48);
        ReflectionTestUtils.setField(service, "adjacentMaxEventAgeHours", 72);

        when(disasterEventRepository.save(any(DisasterEvent.class))).thenAnswer(inv -> {
            DisasterEvent e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", 900L);
            return e;
        });
        when(alertEmbeddingRepository.findEmbeddingText(1L)).thenReturn("[0.1,0.2]");
    }

    private void alert(String type, String message) {
        DisasterAlert a = DisasterAlert.builder()
                .sn(100L).message(message).createdAt(now).disasterType(type)
                .emergencyLevel(DisasterLevel.LEVEL_1).build();
        ReflectionTestUtils.setField(a, "id", 1L);
        a.addRegionCode(REGION);
        when(disasterAlertRepository.findById(1L)).thenReturn(Optional.of(a));
    }

    private static List<Object[]> rows(long firstEventId, double... distances) {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < distances.length; i++) {
            rows.add(new Object[]{firstEventId + i, distances[i]});
        }
        return rows;
    }

    private void sameRegionCandidates(double... distances) {
        when(disasterEventRepository.findTopCandidates(anyString(), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(rows(SAME_EVENT_ID, distances));
    }

    private void adjacentCandidates(double... distances) {
        when(disasterEventRepository.findTopAdjacentCandidates(anyString(), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(rows(ADJACENT_EVENT_ID, distances));
    }

    /** 후보 이벤트 로드(findById) 스텁 — 가드(사고성 유형, 72h 쿨다운)가 이 이벤트의 필드로 판정한다. */
    private void candidateEvent(long eventId, String type, LocalDateTime lastAlertAt) {
        DisasterEvent ev = DisasterEvent.builder()
                .primaryDisasterType(type)
                .firstAlertAt(lastAlertAt.minusHours(5))
                .lastAlertAt(lastAlertAt)
                .alertCount(2)   // 기존 2건 → 병합되면 3 번째 알림
                .build();
        ReflectionTestUtils.setField(ev, "id", eventId);
        events.put(eventId, ev);
        lenient().when(disasterEventRepository.findById(eventId)).thenReturn(Optional.of(ev));
    }

    /** 후보 이벤트 로드 스텁 — firstAlertAt 을 직접 지정(이벤트 나이 상한 가드 검증용). */
    private void candidateEvent(long eventId, String type, LocalDateTime firstAlertAt, LocalDateTime lastAlertAt) {
        DisasterEvent ev = DisasterEvent.builder()
                .primaryDisasterType(type)
                .firstAlertAt(firstAlertAt)
                .lastAlertAt(lastAlertAt)
                .alertCount(2)   // 기존 2건 → 병합되면 3 번째 알림
                .build();
        ReflectionTestUtils.setField(ev, "id", eventId);
        events.put(eventId, ev);
        lenient().when(disasterEventRepository.findById(eventId)).thenReturn(Optional.of(ev));
    }

    private void assertMergedEmbedding(long eventId) {
        ArgumentCaptor<DisasterAlert> mapping = ArgumentCaptor.forClass(DisasterAlert.class);
        verify(disasterAlertRepository).save(mapping.capture());
        assertThat(mapping.getValue().getEventMergeMethod()).isEqualTo(MergeMethod.EMBEDDING);
        // 알림이 붙은 이벤트 id, 순번(기존 2건 + 1 = 3), 이벤트의 알림 수(3)·마지막 알림 시각(now)이 모두 갱신돼야 한다.
        assertThat(mapping.getValue().getEventSequenceNo()).isEqualTo(3);
        assertThat(events.get(eventId).getAlertCount()).isEqualTo(3);
        assertThat(events.get(eventId).getLastAlertAt()).isEqualTo(now);
        // 이벤트 연결은 마지막에 확인한다 - 갱신이 안 된 상태에서도 값 단언이 먼저 원인을 드러내도록.
        assertThat(mapping.getValue().getEvent()).as("알림이 병합 대상 이벤트에 연결돼야 한다").isNotNull()
                .extracting(DisasterEvent::getId).isEqualTo(eventId);
        verify(disasterEventRepository, never()).save(any(DisasterEvent.class));
    }

    private void assertNewEventCreated() {
        verify(disasterEventRepository).save(any(DisasterEvent.class));
        ArgumentCaptor<DisasterAlert> mapping = ArgumentCaptor.forClass(DisasterAlert.class);
        verify(disasterAlertRepository).save(mapping.capture());
        assertThat(mapping.getValue().getEventMergeMethod()).isEqualTo(MergeMethod.SEED);
        // 신규 이벤트로 갔으므로 후보로 등록해 둔 기존 이벤트는 어느 것도 알림 수가 늘지 않았다.
        events.values().forEach(ev -> assertThat(ev.getAlertCount()).isEqualTo(2));
    }

    @Test
    @DisplayName("a) 같은 시군구 후보 없음 + 인접 후보 거리 0.10 + 사고성(화재): 인접 이벤트로 EMBEDDING 병합")
    void adjacentCandidateWithinThreshold_merges() {
        alert("화재", FIRE);
        sameRegionCandidates();
        adjacentCandidates(0.10);
        candidateEvent(ADJACENT_EVENT_ID, "화재", now.minusHours(10));

        service.clusterNewAlert(1L);

        assertMergedEmbedding(ADJACENT_EVENT_ID);
    }

    @Test
    @DisplayName("b) 같은 시군구 후보가 임계값을 통과하면 인접 조회는 호출되지 않는다 [회귀 방지: 뼈대도 통과]")
    void sameRegionMerge_skipsAdjacentLookup() {
        alert("화재", FIRE);
        sameRegionCandidates(0.10);
        adjacentCandidates(0.05);
        candidateEvent(SAME_EVENT_ID, "화재", now.minusHours(10));

        service.clusterNewAlert(1L);

        assertMergedEmbedding(SAME_EVENT_ID);
        verify(disasterEventRepository, never())
                .findTopAdjacentCandidates(anyString(), any(String[].class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("c) 인접 후보 거리 0.30 > 임계 0.15: 병합하지 않고 신규 이벤트, 인접 후보로 LLM 호출 없음")
    void adjacentCandidateOverThreshold_createsNewEventWithoutLlm() {
        alert("화재", FIRE);
        sameRegionCandidates();
        adjacentCandidates(0.30);

        service.clusterNewAlert(1L);

        // 사고성이므로 인접 조회가 일어나야 한다(Red 에서는 호출 자체가 없어 여기서 실패)
        verify(disasterEventRepository)
                .findTopAdjacentCandidates(anyString(), any(String[].class), any(LocalDateTime.class));
        verify(llmDecisionService, never()).pickSameGeneralIncident(anyString(), anyList());
        assertNewEventCreated();
    }

    @Test
    @DisplayName("d) 비사고성 유형(호우): 인접 조회를 하지 않고 신규 이벤트 [회귀 방지: 뼈대도 통과]")
    void nonAccidentType_skipsAdjacentLookup() {
        alert("호우", RAIN);
        sameRegionCandidates();
        adjacentCandidates(0.05);

        service.clusterNewAlert(1L);

        verify(disasterEventRepository, never())
                .findTopAdjacentCandidates(anyString(), any(String[].class), any(LocalDateTime.class));
        assertNewEventCreated();
    }

    @Test
    @DisplayName("e) 인접 조회의 sinceTime 은 알림 createdAt - adjacentWindowHours(48h)")
    void adjacentLookup_usesAdjacentWindowSinceTime() {
        alert("화재", FIRE);
        sameRegionCandidates();
        adjacentCandidates();

        service.clusterNewAlert(1L);

        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(disasterEventRepository)
                .findTopAdjacentCandidates(anyString(), any(String[].class), since.capture());
        assertThat(since.getValue()).isEqualTo(now.minusHours(48));
    }

    @Test
    @DisplayName("f) 인접 후보가 여러 개면 거리 오름차순 첫 번째 통과 후보로 병합 (0.30 은 건너뛰고 0.12)")
    void multipleAdjacentCandidates_mergesIntoFirstWithinThreshold() {
        alert("화재", FIRE);
        sameRegionCandidates();
        adjacentCandidates(0.12, 0.14, 0.30);
        candidateEvent(ADJACENT_EVENT_ID, "화재", now.minusHours(10));
        candidateEvent(ADJACENT_EVENT_ID + 1, "화재", now.minusHours(10));

        service.clusterNewAlert(1L);

        assertMergedEmbedding(ADJACENT_EVENT_ID);
        // 두 번째 후보(0.14)도 임계 이내지만 첫 통과 후보에 이미 붙었으므로 건드리지 않는다.
        assertThat(events.get(ADJACENT_EVENT_ID + 1).getAlertCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("g) 같은 시군구 borderline(LLM 대상) + 인접 임계 통과 후보: 인접 임계값 병합이 LLM 폴백보다 먼저(LLM 미호출)")
    void adjacentThresholdMerge_precedesLlmFallback() {
        alert("화재", FIRE);
        sameRegionCandidates(0.30); // 0.15 < 0.30 <= 0.40 : LLM 폴백 대상
        adjacentCandidates(0.10);
        candidateEvent(ADJACENT_EVENT_ID, "화재", now.minusHours(10));

        service.clusterNewAlert(1L);

        verify(llmDecisionService, never()).pickSameGeneralIncident(anyString(), anyList());
        assertMergedEmbedding(ADJACENT_EVENT_ID);
    }

    @Test
    @DisplayName("h) 임계 이내 인접 후보지만 후보 이벤트가 비사고성(호우): 병합하지 않고 신규 이벤트")
    void adjacentCandidateEventNotAccidentType_createsNewEvent() {
        alert("화재", FIRE);
        sameRegionCandidates();
        adjacentCandidates(0.10);
        candidateEvent(ADJACENT_EVENT_ID, "호우", now.minusHours(10));

        service.clusterNewAlert(1L);

        assertNewEventCreated();
    }

    @Test
    @DisplayName("i) 화재 알림 + 후보 이벤트 마지막 알림 73h 전(첫 알림은 78h 전): 이벤트 나이 상한(첫 알림 기준 72h) 초과로 병합하지 않고 신규 이벤트")
    void adjacentFireCandidateOlderThanCooldown_createsNewEvent() {
        alert("화재", FIRE);
        sameRegionCandidates();
        adjacentCandidates(0.10);
        candidateEvent(ADJACENT_EVENT_ID, "화재", now.minusHours(73));

        service.clusterNewAlert(1L);

        assertNewEventCreated();
    }

    @Test
    @DisplayName("j) 첫 후보(0.10)가 가드 실패(호우)여도 건너뛰고 두 번째 후보(0.12, 임계 이내 + 가드 통과)로 병합")
    void firstCandidateFailsGuard_mergesIntoSecondCandidate() {
        alert("화재", FIRE);
        sameRegionCandidates();
        adjacentCandidates(0.10, 0.12);
        candidateEvent(ADJACENT_EVENT_ID, "호우", now.minusHours(10));
        candidateEvent(ADJACENT_EVENT_ID + 1, "화재", now.minusHours(10));

        service.clusterNewAlert(1L);

        assertMergedEmbedding(ADJACENT_EVENT_ID + 1);
    }

    @Test
    @DisplayName("k) 화재 + 후보 이벤트 마지막 알림이 72h 이내(10h 전): 병합 [회귀 방지: 뼈대도 통과]")
    void adjacentFireCandidateWithinCooldown_merges() {
        alert("화재", FIRE);
        sameRegionCandidates();
        adjacentCandidates(0.10);
        candidateEvent(ADJACENT_EVENT_ID, "화재", now.minusHours(10));

        service.clusterNewAlert(1L);

        assertMergedEmbedding(ADJACENT_EVENT_ID);
    }

    @Test
    @DisplayName("l) 후보 이벤트 findById 가 empty: 건너뛰고 신규 이벤트")
    void adjacentCandidateEventMissing_createsNewEvent() {
        alert("화재", FIRE);
        sameRegionCandidates();
        adjacentCandidates(0.10);
        when(disasterEventRepository.findById(ADJACENT_EVENT_ID)).thenReturn(Optional.empty());

        service.clusterNewAlert(1L);

        assertNewEventCreated();
    }

    @Test
    @DisplayName("m) 기타 후보 이벤트 첫 알림이 100h 전(마지막 5h 전): 이벤트 나이 상한 72h 초과로 병합하지 않고 신규 이벤트")
    void adjacentCandidateOlderThanMaxEventAge_createsNewEvent() {
        alert("기타", FIRE);
        sameRegionCandidates();
        adjacentCandidates(0.10);
        candidateEvent(ADJACENT_EVENT_ID, "기타", now.minusHours(100), now.minusHours(5));

        service.clusterNewAlert(1L);

        assertNewEventCreated();
    }

    @Test
    @DisplayName("n) 기타 후보 이벤트 첫 알림이 71h 전: 병합 [회귀 방지]")
    void adjacentCandidateWithinMaxEventAge_merges() {
        alert("기타", FIRE);
        sameRegionCandidates();
        adjacentCandidates(0.10);
        candidateEvent(ADJACENT_EVENT_ID, "기타", now.minusHours(71), now.minusHours(5));

        service.clusterNewAlert(1L);

        assertMergedEmbedding(ADJACENT_EVENT_ID);
    }

    @Test
    @DisplayName("o) 기타 후보 이벤트 첫 알림이 정확히 72h 전: 경계 포함으로 병합 [회귀 방지]")
    void adjacentCandidateExactlyMaxEventAge_merges() {
        alert("기타", FIRE);
        sameRegionCandidates();
        adjacentCandidates(0.10);
        candidateEvent(ADJACENT_EVENT_ID, "기타", now.minusHours(72), now.minusHours(5));

        service.clusterNewAlert(1L);

        assertMergedEmbedding(ADJACENT_EVENT_ID);
    }

    @Test
    @DisplayName("p) 기타 후보 이벤트 첫 알림이 72h + 1분 전: 상한 초과로 병합하지 않고 신규 이벤트")
    void adjacentCandidateOneMinuteOverMaxEventAge_createsNewEvent() {
        alert("기타", FIRE);
        sameRegionCandidates();
        adjacentCandidates(0.10);
        candidateEvent(ADJACENT_EVENT_ID, "기타", now.minusHours(72).minusMinutes(1), now.minusHours(5));

        service.clusterNewAlert(1L);

        assertNewEventCreated();
    }

    @Test
    @DisplayName("q) 첫 후보(첫 알림 100h 전)는 나이 가드로 건너뛰고 두 번째 후보(10h 전)로 병합")
    void firstCandidateTooOld_mergesIntoSecondCandidate() {
        alert("기타", FIRE);
        sameRegionCandidates();
        adjacentCandidates(0.10, 0.12);
        candidateEvent(ADJACENT_EVENT_ID, "기타", now.minusHours(100), now.minusHours(5));
        candidateEvent(ADJACENT_EVENT_ID + 1, "기타", now.minusHours(10), now.minusHours(5));

        service.clusterNewAlert(1L);

        assertMergedEmbedding(ADJACENT_EVENT_ID + 1);
    }

    @Test
    @DisplayName("r) 화재 후보 이벤트: 마지막 알림은 10h 전이어도 첫 알림이 80h 전이면 나이 상한으로 병합하지 않는다")
    void adjacentFireCandidateOlderThanMaxEventAge_createsNewEvent() {
        alert("화재", FIRE);
        sameRegionCandidates();
        adjacentCandidates(0.10);
        candidateEvent(ADJACENT_EVENT_ID, "화재", now.minusHours(80), now.minusHours(10));

        service.clusterNewAlert(1L);

        assertNewEventCreated();
    }
}
