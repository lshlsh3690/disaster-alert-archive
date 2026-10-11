package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import com.disaster.alert.alertapi.domain.event.model.EventAlertMapping;
import com.disaster.alert.alertapi.domain.event.model.MergeMethod;
import com.disaster.alert.alertapi.domain.event.repository.AlertEmbeddingRepository;
import com.disaster.alert.alertapi.domain.event.repository.DisasterEventRepository;
import com.disaster.alert.alertapi.domain.event.repository.EventAlertMappingRepository;
import com.disaster.alert.alertapi.domain.risk.event.AlertClusteredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 유형별 병합 임계값 완화 검증 — 에피소드형 유형(폭염·한파·대설·강풍·가축질병)만 임베딩 경로 병합 거리 상한을
 * 0.35(유사도 0.65)로 올리고, 나머지 유형은 기존 0.15(유사도 0.85)를 유지한다.
 *
 * <p>순수 Mockito 단위 테스트(DB·Spring 컨텍스트 없음). 폭염·한파는 안내 분기에 걸리지 않는 특보 문구를 쓴다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventClusteringServiceRelaxedThresholdTest {

    private static final String REGION = "4480000000";
    private static final long EXISTING_EVENT_ID = 55L;

    private static final String HEAT = "폭염경보 발효, 야외활동 자제 바랍니다.[홍성군]";
    private static final String COLD = "한파경보 발효, 보온에 유의 바랍니다.[홍성군]";
    private static final String FIRE = "홍성군 OO동 공장 화재 발생, 인근 주민은 대피 바랍니다.";

    /** 구현이 계산식 1.0 - 0.65 로 상한을 만들어도 같은 값이 되도록 테스트도 같은 식을 쓴다. */
    private static final double RELAXED_MAX = 1.0 - 0.65;

    @Mock EmbeddingModel embeddingModel;
    @Mock DisasterAlertRepository disasterAlertRepository;
    @Mock DisasterEventRepository disasterEventRepository;
    @Mock EventAlertMappingRepository eventAlertMappingRepository;
    @Mock AlertEmbeddingRepository alertEmbeddingRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock EventLLMDecisionService llmDecisionService;

    EventClusteringService service;
    LocalDateTime now = LocalDateTime.of(2026, 7, 20, 12, 0);

    @BeforeEach
    void setUp() {
        service = new EventClusteringService(embeddingModel, disasterAlertRepository, disasterEventRepository,
                eventAlertMappingRepository, alertEmbeddingRepository, eventPublisher, llmDecisionService);
        ReflectionTestUtils.setField(service, "similarityThreshold", 0.85);
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
        ReflectionTestUtils.setField(service, "advisorySplitTypesCsv", "산불,폭염,한파");
        ReflectionTestUtils.setField(service, "relaxedThresholdTypesCsv", "폭염,한파,대설,강풍,가축질병");
        ReflectionTestUtils.setField(service, "relaxedSimilarityThreshold", 0.65);
        ReflectionTestUtils.setField(service, "episodicTypesCsv", "");

        when(disasterEventRepository.save(any(DisasterEvent.class))).thenAnswer(inv -> {
            DisasterEvent e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", 900L);
            return e;
        });
        when(alertEmbeddingRepository.findEmbeddingText(1L)).thenReturn("[0.1,0.2]");
        when(eventAlertMappingRepository.countByEventId(anyLong())).thenReturn(2);
    }

    private static long anyLong() {
        return org.mockito.ArgumentMatchers.anyLong();
    }

    private void alert(String type, String message) {
        DisasterAlert a = DisasterAlert.builder()
                .sn(100L).message(message).createdAt(now).disasterType(type)
                .emergencyLevel(DisasterLevel.LEVEL_1).build();
        ReflectionTestUtils.setField(a, "id", 1L);
        a.addRegionCode(REGION);
        when(disasterAlertRepository.findById(1L)).thenReturn(Optional.of(a));
    }

    /** 거리 ASC 로 주어진 후보들. 첫 후보의 eventId 는 55, 이후 56, 57... */
    private void candidates(double... distances) {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < distances.length; i++) {
            rows.add(new Object[]{EXISTING_EVENT_ID + i, distances[i]});
        }
        when(disasterEventRepository.findTopCandidates(anyString(), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(rows);
    }

    private void run(String type, String message, double... distances) {
        alert(type, message);
        candidates(distances);
        service.clusterNewAlert(1L);
    }

    private void assertMergedEmbedding(long eventId) {
        ArgumentCaptor<EventAlertMapping> mapping = ArgumentCaptor.forClass(EventAlertMapping.class);
        verify(eventAlertMappingRepository).save(mapping.capture());
        assertThat(mapping.getValue().getMergeMethod()).isEqualTo(MergeMethod.EMBEDDING);
        verify(disasterEventRepository).incrementOnMerge(eventId, now);
        verify(eventPublisher).publishEvent(new AlertClusteredEvent(eventId, 1L));
        verify(disasterEventRepository, never()).save(any(DisasterEvent.class));
    }

    private void assertNewEventCreated() {
        verify(disasterEventRepository).save(any(DisasterEvent.class));
        ArgumentCaptor<EventAlertMapping> mapping = ArgumentCaptor.forClass(EventAlertMapping.class);
        verify(eventAlertMappingRepository).save(mapping.capture());
        assertThat(mapping.getValue().getMergeMethod()).isEqualTo(MergeMethod.SEED);
        verify(disasterEventRepository, never()).incrementOnMerge(any(), any());
    }

    @Test
    @DisplayName("1) 허용 유형(폭염 특보): 후보 거리 0.30 은 완화 상한 0.35 이내라 EMBEDDING 머지되고 신규 이벤트는 없다")
    void relaxedType_distance030_merges() {
        run("폭염", HEAT, 0.30);

        assertMergedEmbedding(EXISTING_EVENT_ID);
    }

    @Test
    @DisplayName("2) 허용 유형이라도 후보 거리 0.36 은 완화 상한 0.35 초과 - 머지하지 않고 신규 이벤트 [회귀 방지: 뼈대도 통과]")
    void relaxedType_distance036_createsNewEvent() {
        run("폭염", HEAT, 0.36);

        assertNewEventCreated();
    }

    @Test
    @DisplayName("3) 경계 포함: 거리가 정확히 상한(1.0 - 0.65)이면 머지된다")
    void relaxedType_boundaryInclusive() {
        run("폭염", HEAT, RELAXED_MAX);

        assertMergedEmbedding(EXISTING_EVENT_ID);
    }

    @Test
    @DisplayName("3b) 경계 바로 위(상한 + 0.001)는 머지되지 않고 신규 이벤트 [회귀 방지: 뼈대도 통과]")
    void relaxedType_justAboveBoundary_createsNewEvent() {
        run("폭염", HEAT, RELAXED_MAX + 0.001);

        assertNewEventCreated();
    }

    @ParameterizedTest(name = "4) 허용 유형 {0}: 거리 0.30 에서 머지")
    @CsvSource(delimiter = '|', value = {
            "폭염|폭염경보 발효, 야외활동 자제 바랍니다.[홍성군]",
            "한파|한파경보 발효, 보온에 유의 바랍니다.[홍성군]",
            "대설|대설주의보 발효 중, 차량 운행에 주의 바랍니다.[홍성군]",
            "강풍|강풍주의보 발효 중, 시설물 관리에 유의 바랍니다.[홍성군]",
            "가축질병|구제역 발생, 축산농가 이동 제한 및 소독 바랍니다.[홍성군]"
    })
    void allRelaxedTypes_distance030_merge(String type, String message) {
        run(type, message, 0.30);

        assertMergedEmbedding(EXISTING_EVENT_ID);
    }

    @ParameterizedTest(name = "5) 비허용 비사고성 유형 {0}: 거리 0.30 이어도 머지하지 않고 신규 [회귀 방지: 뼈대도 통과]")
    @CsvSource(delimiter = '|', value = {
            "호우|호우주의보 발효 중, 하천 접근을 자제 바랍니다.[홍성군]",
            "풍랑|풍랑주의보 발효 중, 해상 활동을 자제 바랍니다.[홍성군]"
    })
    void nonRelaxedNonAccidentType_distance030_createsNewEvent(String type, String message) {
        run(type, message, 0.30);

        assertNewEventCreated();
    }

    @Test
    @DisplayName("6) 사고성 유형(화재)은 거리 0.30 에서 임베딩 머지되지 않는다 - LLM 폴백 경로(후보 이벤트 없음)라 신규 이벤트 [회귀 방지: 뼈대도 통과]")
    void accidentType_distance030_notEmbeddingMerged() {
        run("화재", FIRE, 0.30);

        verify(eventAlertMappingRepository, never())
                .save(org.mockito.ArgumentMatchers.argThat(m -> m.getMergeMethod() == MergeMethod.EMBEDDING));
        verify(disasterEventRepository, never()).incrementOnMerge(any(), any());
        // borderline(0.15 < 0.30 <= 0.40) 후보는 LLM 폴백 대상이지만 후보 이벤트 조회가 비어 LLM 은 호출되지 않는다
        verify(llmDecisionService, never()).pickSameGeneralIncident(anyString(), anyList());
        assertNewEventCreated();
    }

    @ParameterizedTest(name = "7) 거리 0.15 이하는 {0} 에서도 기존대로 머지 [회귀 방지: 뼈대도 통과]")
    @CsvSource(delimiter = '|', value = {
            "폭염|폭염경보 발효, 야외활동 자제 바랍니다.[홍성군]",
            "호우|호우주의보 발효 중, 하천 접근을 자제 바랍니다.[홍성군]"
    })
    void distanceWithinBaseThreshold_mergesForAllTypes(String type, String message) {
        run(type, message, 0.10);

        assertMergedEmbedding(EXISTING_EVENT_ID);
    }

    @Test
    @DisplayName("8a) relaxedThresholdTypes 가 빈 문자열이면 폭염도 기존 0.15 를 쓴다 - 거리 0.30 은 머지되지 않는다 [뼈대도 통과]")
    void emptyRelaxedTypes_heatUsesBaseThreshold() {
        ReflectionTestUtils.setField(service, "relaxedThresholdTypesCsv", "");

        run("폭염", HEAT, 0.30);

        assertNewEventCreated();
    }

    @Test
    @DisplayName("8b) CSV 공백(\"폭염, 한파\")은 trim 되어 한파도 허용 유형으로 인식된다 - 거리 0.30 머지")
    void relaxedTypesCsv_trimsWhitespace() {
        ReflectionTestUtils.setField(service, "relaxedThresholdTypesCsv", "폭염, 한파");

        run("한파", COLD, 0.30);

        assertMergedEmbedding(EXISTING_EVENT_ID);
    }

    @Test
    @DisplayName("9a) relaxedSimilarityThreshold=0.75(거리 0.25): 허용 유형의 거리 0.30 은 머지되지 않는다 [뼈대도 통과]")
    void customRelaxedThreshold_distance030_notMerged() {
        ReflectionTestUtils.setField(service, "relaxedSimilarityThreshold", 0.75);

        run("폭염", HEAT, 0.30);

        assertNewEventCreated();
    }

    @Test
    @DisplayName("9b) relaxedSimilarityThreshold=0.75(거리 0.25): 허용 유형의 거리 0.20 은 머지된다")
    void customRelaxedThreshold_distance020_merges() {
        ReflectionTestUtils.setField(service, "relaxedSimilarityThreshold", 0.75);

        run("폭염", HEAT, 0.20);

        assertMergedEmbedding(EXISTING_EVENT_ID);
    }

    @Test
    @DisplayName("10) 후보가 여러 개(거리 오름차순)면 완화 상한 이내인 첫 후보에 머지된다")
    void multipleCandidates_mergesIntoFirstWithinRelaxedLimit() {
        run("폭염", HEAT, 0.30, 0.34, 0.50);

        assertMergedEmbedding(EXISTING_EVENT_ID);
        verify(disasterEventRepository, never()).incrementOnMerge(eq(EXISTING_EVENT_ID + 1), any());
    }
}
