package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.event.model.ClusteringTrace;
import com.disaster.alert.alertapi.domain.event.model.ClusteringTracePath;
import com.disaster.alert.alertapi.domain.event.model.ClusteringTraceResult;
import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import com.disaster.alert.alertapi.domain.event.model.EventAlertMapping;
import com.disaster.alert.alertapi.domain.event.model.MergeMethod;
import com.disaster.alert.alertapi.domain.event.repository.AlertEmbeddingRepository;
import com.disaster.alert.alertapi.domain.event.repository.ClusteringTraceRepository;
import com.disaster.alert.alertapi.domain.event.repository.DisasterEventRepository;
import com.disaster.alert.alertapi.domain.event.repository.EventAlertMappingRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.doubleThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 동물 탈출·출몰 알림이 {@code doCluster} 안의 동물 경로(실종자 다음)에서 처리되는지 검증한다.
 * 기준: docs/event-clustering/CLUSTERING_CRITERIA.md 3절 2번 동물 경로. 순수 Mockito 단위 테스트(DB·Spring 없음).
 *
 * <p>Red 단계에서는 동물 알림이 아직 기존 임베딩 경로(findTopCandidates -> 새 이벤트)를 타므로
 * 동물 후보 쿼리 verify("Wanted but not invoked") 또는 path 단언(EMBEDDING != ANIMAL)에서 실패한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventClusteringServiceAnimalTest {

    private static final String REGION = "4480000000";           // 충남 홍성군 -> 시도 "44", 시군구 "44800"
    private static final long ALERT_ID = 1L;
    private static final String EMBEDDING = "[0.1,0.2]";
    private static final String WOLF = "[홍성군] 인근 늑대 탈출, 안전에 유의하시기 바랍니다.";
    private static final String BOAR = "[홍성군] 인근 멧돼지 출몰, 안전에 유의하시기 바랍니다.";

    @Mock EmbeddingModel embeddingModel;
    @Mock DisasterAlertRepository disasterAlertRepository;
    @Mock DisasterEventRepository disasterEventRepository;
    @Mock EventAlertMappingRepository eventAlertMappingRepository;
    @Mock AlertEmbeddingRepository alertEmbeddingRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock EventLLMDecisionService llmDecisionService;
    @Mock ClusteringTraceRepository clusteringTraceRepository;

    EventClusteringService service;
    LocalDateTime now = LocalDateTime.of(2026, 7, 20, 12, 0);

    @BeforeEach
    void setUp() {
        service = new EventClusteringService(embeddingModel, disasterAlertRepository, disasterEventRepository,
                eventAlertMappingRepository, alertEmbeddingRepository, eventPublisher, llmDecisionService,
                clusteringTraceRepository);
        // 운영 기본값(application.yml)과 같게 맞춘다 - 동물 경로의 윈도우·후보 수·거리·병합 상한이 이 값들에 의존한다.
        ReflectionTestUtils.setField(service, "similarityThreshold", 0.85);
        ReflectionTestUtils.setField(service, "candidateWindowHours", 168);
        ReflectionTestUtils.setField(service, "maxRegionSpan", 10);
        ReflectionTestUtils.setField(service, "nationwideSidoSpan", 8);
        ReflectionTestUtils.setField(service, "personWindowHours", 336);
        ReflectionTestUtils.setField(service, "llmFallbackDistanceCeil", 0.40);
        ReflectionTestUtils.setField(service, "accidentTypesCsv", "기타,화재,산불,붕괴,교통사고");
        ReflectionTestUtils.setField(service, "animalKeywords", "탈출|출몰|멧돼지|들개|늑대");
        ReflectionTestUtils.setField(service, "globalTypesCsv", "태풍");
        ReflectionTestUtils.setField(service, "globalWindowHours", 168);
        ReflectionTestUtils.setField(service, "regionalTypesCsv", "산불:168,산사태:168,홍수:168");
        ReflectionTestUtils.setField(service, "regionalAdjacentTypesCsv", "산불");
        ReflectionTestUtils.setField(service, "regionalAdjacentWindowHours", 24);
        ReflectionTestUtils.setField(service, "relaxedThresholdTypesCsv", "폭염,한파,대설,강풍,가축질병");
        ReflectionTestUtils.setField(service, "relaxedSimilarityThreshold", 0.65);
        ReflectionTestUtils.setField(service, "episodicTypesCsv", "폭염,한파,대설,강풍,가축질병");
        ReflectionTestUtils.setField(service, "episodicWindowHours", 168);
        ReflectionTestUtils.setField(service, "adjacentWindowHours", 48);
        ReflectionTestUtils.setField(service, "adjacentMaxEventAgeHours", 72);
        ReflectionTestUtils.setField(service, "crossRegionWindowHours", 336);
        ReflectionTestUtils.setField(service, "crossRegionTopK", 5);
        ReflectionTestUtils.setField(service, "crossRegionSimilarityFloor", 0.6);
        ReflectionTestUtils.setField(service, "crossRegionSpanCapAlerts", 30);
        ReflectionTestUtils.setField(service, "crossRegionSpanCapSido", 10);

        when(disasterEventRepository.save(any(DisasterEvent.class))).thenAnswer(inv -> {
            DisasterEvent e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", 99L);   // 신규 이벤트 id - trace.eventId 가 이 값이어야 한다
            return e;
        });
        when(alertEmbeddingRepository.findEmbeddingText(ALERT_ID)).thenReturn(EMBEDDING);
        when(eventAlertMappingRepository.countByEventId(anyLong())).thenReturn(2);
    }

    /** 유형 '기타' 알림을 만든다. withRegion=false 면 지역 코드를 붙이지 않는다. */
    private DisasterAlert alert(String message, boolean withRegion) {
        DisasterAlert a = DisasterAlert.builder()
                .sn(100L).message(message).createdAt(now).disasterType("기타")
                .emergencyLevel(DisasterLevel.LEVEL_1).build();
        ReflectionTestUtils.setField(a, "id", ALERT_ID);
        if (withRegion) {
            a.addRegionCode(REGION);
        }
        when(disasterAlertRepository.findById(ALERT_ID)).thenReturn(Optional.of(a));
        return a;
    }

    /** 후보 이벤트 {id} 를 병합 상한 판정용으로 등록한다: 알림 수 alertCount, 걸친 시도 수 sidoCount. */
    private void stubCandidateEvent(long id, int alertCount, int sidoCount) {
        DisasterEvent ev = DisasterEvent.builder().primaryDisasterType("기타").alertCount(alertCount)
                .firstAlertAt(now.minusDays(2)).lastAlertAt(now.minusDays(1)).build();
        ReflectionTestUtils.setField(ev, "id", id);
        when(disasterEventRepository.findById(id)).thenReturn(Optional.of(ev));
        when(eventAlertMappingRepository.countDistinctSidoByEventId(id)).thenReturn(sidoCount);
    }

    private ClusteringTrace capturedTrace() {
        ArgumentCaptor<ClusteringTrace> captor = ArgumentCaptor.forClass(ClusteringTrace.class);
        verify(clusteringTraceRepository).upsert(captor.capture());
        return captor.getValue();
    }

    // 결정: 이동종(늑대 등)은 시도 단위 후보 쿼리로 찾는다. 인자는 하나라도 어긋나면 사고가 난다 -
    //       until 이 알림 시각이어야 판정 순간에 없던 미래 이벤트가 후보에 안 들어오고, selfEventId=-1 은 "제외할 자기 이벤트 없음",
    //       maxDistance 0.4 = 1 - similarity-floor(0.6). LLM 이 후보 중 고르면 그 이벤트에 LLM 방식으로 병합하고,
    //       임베딩 경로(findTopCandidates)·LLM 폴백(pickSameGeneralIncident)은 타지 않는다.
    // Red: 늑대 알림이 기존 임베딩 경로를 타서 "Wanted but not invoked: findCrossRegionCandidatesSido" 로 실패한다.
    @Test
    @DisplayName("이동종(늑대): 시도 후보 쿼리를 정해진 인자로 부르고 LLM 이 고른 이벤트에 LLM 방식으로 병합, 임베딩·폴백 경로는 안 탄다")
    void movingSpecies_queriesSidoCandidatesAndMergesLlmPick() {
        DisasterAlert alert = alert(WOLF, true);
        // 후보 두 건: 10번(거리 0.20), 11번(거리 0.30). LLM 이 두 번째(index 1)를 고르게 해 "첫 후보 자동 선택"이 아님을 확인한다.
        when(disasterEventRepository.findCrossRegionCandidatesSido(anyString(), any(), any(), anyLong(), anyString(),
                anyList(), anyDouble(), anyInt()))
                .thenReturn(List.<Object[]>of(new Object[]{10L, 0.20}, new Object[]{11L, 0.30}));
        when(eventAlertMappingRepository.findRepresentativeMessages(anyList()))
                .thenReturn(List.<Object[]>of(new Object[]{10L, "대표 본문 10"}, new Object[]{11L, "대표 본문 11"}));
        when(llmDecisionService.pickSameIncident(anyString(), anyList())).thenReturn(1);
        stubCandidateEvent(11L, 3, 2);   // 상한(30건/10개 시도)에 한참 못 미치는 이벤트 - 병합 허용

        service.clusterNewAlert(ALERT_ID);

        // 시도 쿼리: 시도 코드 "44"(홍성군 앞 2자), 종 정규식 "늑대", since = 알림 시각 - 336h, until = 알림 시각.
        verify(disasterEventRepository).findCrossRegionCandidatesSido(eq(EMBEDDING), eq(now.minusHours(336)), eq(now),
                eq(-1L), eq("늑대"), eq(List.of("44")), doubleThat(d -> Math.abs(d - 0.4) < 1e-9), eq(5));
        verify(disasterEventRepository, never()).findCrossRegionCandidatesSigungu(any(), any(), any(), any(), any(),
                any(), anyDouble(), anyInt());
        // LLM 에는 후보 두 건이 거리순 그대로, 대표 본문과 함께 전달된다.
        verify(llmDecisionService).pickSameIncident(WOLF, List.of(
                new EventLLMDecisionService.Candidate(10L, "대표 본문 10"),
                new EventLLMDecisionService.Candidate(11L, "대표 본문 11")));
        // 동물 경로를 탄 알림은 임베딩 경로와 사고성 LLM 폴백을 거치지 않는다.
        verify(disasterEventRepository, never()).findTopCandidates(any(), any(), any());
        verify(llmDecisionService, never()).pickSameGeneralIncident(any(), any());

        // 병합 대상은 LLM 이 고른 11번, 방식은 LLM.
        ArgumentCaptor<EventAlertMapping> mapping = ArgumentCaptor.forClass(EventAlertMapping.class);
        verify(eventAlertMappingRepository).save(mapping.capture());
        assertThat(mapping.getValue().getId().getEventId()).isEqualTo(11L);
        assertThat(mapping.getValue().getMergeMethod()).isEqualTo(MergeMethod.LLM);
        verify(disasterEventRepository).incrementOnMerge(11L, alert.getCreatedAt());

        ClusteringTrace trace = capturedTrace();
        assertThat(trace.getPath()).isEqualTo(ClusteringTracePath.ANIMAL);
        assertThat(trace.getResult()).isEqualTo(ClusteringTraceResult.MERGED);
        assertThat(trace.getEventId()).isEqualTo(11L);
    }

    // 결정: 토착종(멧돼지·들개·뱀)은 국지적으로만 움직이므로 시도가 아니라 시군구 단위 쿼리를 쓴다.
    //       시도 단위를 쓰면 인접 시도를 타고 전국이 한 사건으로 묶이는 과병합이 난다.
    // Red: 멧돼지 알림이 기존 임베딩 경로를 타서 "Wanted but not invoked: findCrossRegionCandidatesSigungu" 로 실패한다.
    @Test
    @DisplayName("토착종(멧돼지): 시군구 후보 쿼리를 시군구 코드(앞 5자)로 부르고 시도 쿼리는 부르지 않는다")
    void endemicSpecies_queriesSigunguCandidatesNotSido() {
        alert(BOAR, true);
        when(disasterEventRepository.findCrossRegionCandidatesSigungu(anyString(), any(), any(), anyLong(), anyString(),
                anyList(), anyDouble(), anyInt())).thenReturn(List.of());

        service.clusterNewAlert(ALERT_ID);

        // 시군구 코드 "44800"(홍성군 앞 5자), 종 정규식 "멧돼지".
        verify(disasterEventRepository).findCrossRegionCandidatesSigungu(eq(EMBEDDING), eq(now.minusHours(336)), eq(now),
                eq(-1L), eq("멧돼지"), eq(List.of("44800")), doubleThat(d -> Math.abs(d - 0.4) < 1e-9), eq(5));
        verify(disasterEventRepository, never()).findCrossRegionCandidatesSido(any(), any(), any(), any(), any(),
                any(), anyDouble(), anyInt());
    }

    // 결정: 후보가 0건이면 LLM 을 부르지 않고(호출당 비용) 곧바로 새 이벤트를 만든다.
    // Red: 동물 경로가 없어 path 가 EMBEDDING 으로 남아 "expected ANIMAL but was EMBEDDING" 으로 실패한다.
    @Test
    @DisplayName("후보 0건: LLM 을 부르지 않고 새 이벤트(경로는 ANIMAL)")
    void noCandidates_skipsLlmAndCreatesNewEvent() {
        alert(WOLF, true);
        when(disasterEventRepository.findCrossRegionCandidatesSido(anyString(), any(), any(), anyLong(), anyString(),
                anyList(), anyDouble(), anyInt())).thenReturn(List.of());

        service.clusterNewAlert(ALERT_ID);

        ClusteringTrace trace = capturedTrace();
        assertThat(trace.getPath()).isEqualTo(ClusteringTracePath.ANIMAL);
        assertThat(trace.getResult()).isEqualTo(ClusteringTraceResult.NEW);
        verify(llmDecisionService, never()).pickSameIncident(any(), any());
    }

    // 결정: 지역 코드가 없는 동물 알림은 후보를 찾을 단위(시도/시군구)가 없으므로 동물 경로를 건너뛰고
    //       기존 임베딩 경로의 NO_REGION(항상 새 이벤트)으로 간다. 빈 코드로 쿼리를 날리면 IN () 오류가 난다.
    // Red: 이 테스트는 Red 에서도 통과한다 - 현재 동작이 곧 기대 동작이다. Green 에서 동물 분기를 넣다가
    //      지역 검사를 빠뜨리면 여기서 깨지도록 고정하는 회귀 방어다.
    @Test
    @DisplayName("지역 코드 없음: 동물 쿼리를 부르지 않고 NO_REGION 경로로 새 이벤트")
    void noRegion_skipsAnimalPathAndUsesNoRegion() {
        alert(WOLF, false);

        service.clusterNewAlert(ALERT_ID);

        verify(disasterEventRepository, never()).findCrossRegionCandidatesSido(any(), any(), any(), any(), any(),
                any(), anyDouble(), anyInt());
        verify(disasterEventRepository, never()).findCrossRegionCandidatesSigungu(any(), any(), any(), any(), any(),
                any(), anyDouble(), anyInt());
        ClusteringTrace trace = capturedTrace();
        assertThat(trace.getPath()).isEqualTo(ClusteringTracePath.NO_REGION);
        assertThat(trace.getResult()).isEqualTo(ClusteringTraceResult.NEW);
    }
}
