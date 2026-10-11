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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 안내성 `기타`(물놀이·수상안전 예방 안내문) 의 시군구별 안내 이벤트 분리 검증.
 *
 * <p>안내문이 인접 시군구 병합으로 7개 시군구·44일짜리 이벤트가 되던 것을 막기 위해, 안내성 알림은 임베딩·후보
 * 조회·LLM 없이 시군구별 안내 이벤트(is_advisory=true)로 모으고 이웃 병합은 하지 않는다. 시군구가 2개 이상이면
 * 에피소드형과 같이 broadcast 경로로 보낸다. 순수 Mockito 단위 테스트(DB·Spring 컨텍스트 없음).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventClusteringServiceAdvisoryTypeTest {

    private static final String REGION = "4373000000"; // 옥천군
    private static final long EXISTING_EVENT_ID = 77L;
    private static final String[] SAME_SIDO_CODES = {"4375000000", "4376000000", "4377000000"};

    private static final String WATER_GUIDE =
            "수상안전사고 예방을 위해 ▲구명조끼 착용 ▲위험구역 진입금지 ▲야간, 음주수영 금지 ▲어린이는 반드시 보호자 동반 등 "
                    + "안전수칙을 준수하시기 바랍니다. [옥천군]";
    private static final String DEER_ESCAPE =
            "광명시 옥길동151 인근 사슴5마리 탈출. 주민께서는 안전에 유의하시고 발견 시 신고 바랍니다.";

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
        ReflectionTestUtils.setField(service, "advisorySplitTypesCsv", "산불,기타");
        ReflectionTestUtils.setField(service, "relaxedThresholdTypesCsv", "폭염,한파,대설,강풍,가축질병");
        ReflectionTestUtils.setField(service, "relaxedSimilarityThreshold", 0.65);
        ReflectionTestUtils.setField(service, "episodicTypesCsv", "폭염,한파,대설,강풍,가축질병");
        ReflectionTestUtils.setField(service, "episodicWindowHours", 168);
        ReflectionTestUtils.setField(service, "advisoryWindowHours", 168);

        when(disasterEventRepository.save(any(DisasterEvent.class))).thenAnswer(inv -> {
            DisasterEvent e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", 900L);
            return e;
        });
        when(alertEmbeddingRepository.findEmbeddingText(1L)).thenReturn("[0.1,0.2]");
        when(disasterEventRepository.findTopCandidates(anyString(), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(eventAlertMappingRepository.countByEventId(EXISTING_EVENT_ID)).thenReturn(3);
    }

    private DisasterAlert alert(String type, String message, DisasterLevel level, String... regionCodes) {
        DisasterAlert a = DisasterAlert.builder()
                .sn(100L).message(message).createdAt(now).disasterType(type)
                .emergencyLevel(level).build();
        ReflectionTestUtils.setField(a, "id", 1L);
        for (String code : regionCodes) {
            a.addRegionCode(code);
        }
        when(disasterAlertRepository.findById(1L)).thenReturn(Optional.of(a));
        return a;
    }

    private void givenAdvisoryTarget() {
        when(disasterEventRepository.findAdvisoryMergeTarget(anyString(), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(Optional.of(EXISTING_EVENT_ID));
    }

    private void givenNoAdvisoryTarget() {
        when(disasterEventRepository.findAdvisoryMergeTarget(anyString(), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
    }

    /** 임베딩·후보(지역/인접) 조회·LLM 이 전혀 일어나지 않았음. */
    private void verifyEmbeddingAndCandidatePathsSkipped() {
        verify(embeddingModel, never()).embed(anyString());
        verify(disasterEventRepository, never())
                .findTopCandidates(anyString(), any(String[].class), any(LocalDateTime.class));
        verify(disasterEventRepository, never())
                .findTopAdjacentCandidates(anyString(), any(String[].class), any(LocalDateTime.class));
        verifyNoInteractions(llmDecisionService);
    }

    private void verifyAdvisoryBranchNotTaken() {
        verify(disasterEventRepository, never())
                .findAdvisoryMergeTarget(anyString(), any(String[].class), any(LocalDateTime.class));
        verify(disasterEventRepository)
                .findTopCandidates(anyString(), any(String[].class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("a) 기타 물놀이 안내 + 시군구 1개 + 안내 이벤트 없음: 신규 안내 이벤트 저장, 임베딩·후보·LLM 미호출")
    void waterGuide_createsNewAdvisoryEvent() {
        alert("기타", WATER_GUIDE, DisasterLevel.LEVEL_1, REGION);
        givenNoAdvisoryTarget();

        service.clusterNewAlert(1L);

        verifyEmbeddingAndCandidatePathsSkipped();
        ArgumentCaptor<DisasterEvent> event = ArgumentCaptor.forClass(DisasterEvent.class);
        verify(disasterEventRepository).save(event.capture());
        assertThat(event.getValue().isAdvisory()).isTrue();
        assertThat(event.getValue().getPrimaryDisasterType()).isEqualTo("기타");
        ArgumentCaptor<EventAlertMapping> mapping = ArgumentCaptor.forClass(EventAlertMapping.class);
        verify(eventAlertMappingRepository).save(mapping.capture());
        assertThat(mapping.getValue().getMergeMethod()).isEqualTo(MergeMethod.SEED);
        verify(eventPublisher).publishEvent(new AlertClusteredEvent(900L, 1L));
    }

    @Test
    @DisplayName("b) 기존 안내 이벤트가 있으면 ADVISORY 로 병합, window 는 알림 시각 - 168h, 시군구 인자는 앞 5자")
    void waterGuide_mergesIntoExistingAdvisoryEvent() {
        alert("기타", WATER_GUIDE, DisasterLevel.LEVEL_1, REGION);
        givenAdvisoryTarget();

        service.clusterNewAlert(1L);

        verifyEmbeddingAndCandidatePathsSkipped();
        verify(disasterEventRepository).findAdvisoryMergeTarget(
                eq("기타"), eq(new String[]{"43730"}), eq(now.minusHours(168)));
        ArgumentCaptor<EventAlertMapping> mapping = ArgumentCaptor.forClass(EventAlertMapping.class);
        verify(eventAlertMappingRepository).save(mapping.capture());
        assertThat(mapping.getValue().getMergeMethod()).isEqualTo(MergeMethod.ADVISORY);
        verify(disasterEventRepository).incrementOnMerge(EXISTING_EVENT_ID, now);
        verify(eventPublisher).publishEvent(new AlertClusteredEvent(EXISTING_EVENT_ID, 1L));
        verify(disasterEventRepository, never()).save(any(DisasterEvent.class));
    }

    @Test
    @DisplayName("b2) advisoryWindowHours=72 로 바꾸면 window 는 알림 시각 - 72h")
    void waterGuide_windowFollowsConfig() {
        ReflectionTestUtils.setField(service, "advisoryWindowHours", 72);
        alert("기타", WATER_GUIDE, DisasterLevel.LEVEL_1, REGION);
        givenAdvisoryTarget();

        service.clusterNewAlert(1L);

        verify(disasterEventRepository).findAdvisoryMergeTarget(
                eq("기타"), any(String[].class), eq(now.minusHours(72)));
    }

    @Test
    @DisplayName("c) 시군구 2개 이상 안내문은 broadcast 경로 - 안내 이벤트 조회·임베딩·후보 조회 없이 broadcast 이벤트 저장")
    void waterGuide_multiRegionGoesBroadcast() {
        alert("기타", WATER_GUIDE, DisasterLevel.LEVEL_1, SAME_SIDO_CODES);
        givenAdvisoryTarget();

        service.clusterNewAlert(1L);

        verifyEmbeddingAndCandidatePathsSkipped();
        verify(disasterEventRepository, never())
                .findAdvisoryMergeTarget(anyString(), any(String[].class), any(LocalDateTime.class));
        ArgumentCaptor<DisasterEvent> event = ArgumentCaptor.forClass(DisasterEvent.class);
        verify(disasterEventRepository).save(event.capture());
        assertThat(event.getValue().isBroadcast()).isTrue();
    }

    @Test
    @DisplayName("d) 기타 비안내(사슴 탈출)는 안내 분기를 타지 않고 기존 임베딩 경로 [회귀 보호]")
    void nonAdvisoryEtc_goesEmbeddingPath() {
        alert("기타", DEER_ESCAPE, DisasterLevel.LEVEL_1, REGION);
        givenAdvisoryTarget();

        service.clusterNewAlert(1L);

        verifyAdvisoryBranchNotTaken();
    }

    @Test
    @DisplayName("e) advisorySplitTypesCsv=산불(기타 미포함)이면 물놀이 안내여도 기존 경로 [회귀 보호]")
    void etcNotInSplitTypes_goesEmbeddingPath() {
        ReflectionTestUtils.setField(service, "advisorySplitTypesCsv", "산불");
        alert("기타", WATER_GUIDE, DisasterLevel.LEVEL_1, REGION);
        givenAdvisoryTarget();

        service.clusterNewAlert(1L);

        verifyAdvisoryBranchNotTaken();
    }

    @Test
    @DisplayName("f) LEVEL_2 물놀이 안내 문구는 사건 취급 - 안내 분기를 타지 않는다 [회귀 보호]")
    void highLevelWaterGuide_goesEmbeddingPath() {
        alert("기타", WATER_GUIDE, DisasterLevel.LEVEL_2, REGION);
        givenAdvisoryTarget();

        service.clusterNewAlert(1L);

        verifyAdvisoryBranchNotTaken();
    }
}
