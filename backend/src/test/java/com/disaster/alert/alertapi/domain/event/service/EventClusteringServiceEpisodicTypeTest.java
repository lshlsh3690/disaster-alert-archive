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
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.Mockito.when;

/**
 * 에피소드형 유형(폭염·한파·대설·강풍·가축질병)의 "유형 + 시군구" 키 병합 검증.
 *
 * <p>같은 시군구에서 같은 유형의 알림이 끊김 없이 이어지는 동안은 문장 유사도·안내/특보 구분과 무관하게
 * 하나의 이벤트로 합친다(연쇄 병합은 의도). 끊김 기준은 마지막 알림 이후 {@code episodic-window-hours}(기본 168h).
 * 시군구가 정확히 1개일 때만 키 병합하고, 다지역(2개 이상)·광역 알림은 지역 이벤트 후보에서 제외해
 * broadcast 경로로 보낸다(시군구 간 다리로 이벤트가 도 단위 blob 이 되는 것 방지). 사고성 유형의 다지역은
 * 전파 허용을 위해 기존 임베딩 경로를 유지한다.
 *
 * <p>순수 Mockito 단위 테스트(DB·Spring 컨텍스트 없음). 폭염·한파의 안내성 분리는 이 규칙으로 대체되므로
 * {@code advisorySplitTypesCsv} 는 새 기본값 "산불" 로 둔다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventClusteringServiceEpisodicTypeTest {

    private static final String REGION = "4480000000";
    private static final long EXISTING_EVENT_ID = 55L;

    private static final String HEAT_GUIDE =
            "무더위가 지속되고 있습니다. ▲야외 활동 자제 ▲무더위쉼터 이용 ▲충분한 물 섭취[홍성군]";
    private static final String HEAT_WARNING = "홍성 폭염특보 발효 중, 야외활동 자제 바랍니다.[홍성군]";

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
        ReflectionTestUtils.setField(service, "advisorySplitTypesCsv", "산불");
        ReflectionTestUtils.setField(service, "relaxedThresholdTypesCsv", "폭염,한파,대설,강풍,가축질병");
        ReflectionTestUtils.setField(service, "relaxedSimilarityThreshold", 0.65);
        ReflectionTestUtils.setField(service, "episodicTypesCsv", "폭염,한파,대설,강풍,가축질병");
        ReflectionTestUtils.setField(service, "episodicWindowHours", 168);

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

    private DisasterAlert alert(String type, String message, String... regionCodes) {
        DisasterAlert a = DisasterAlert.builder()
                .sn(100L).message(message).createdAt(now).disasterType(type)
                .emergencyLevel(DisasterLevel.LEVEL_1).build();
        ReflectionTestUtils.setField(a, "id", 1L);
        for (String code : regionCodes) {
            a.addRegionCode(code);
        }
        when(disasterAlertRepository.findById(1L)).thenReturn(Optional.of(a));
        return a;
    }

    private void givenMergeTarget() {
        when(disasterEventRepository.findRegionalTypeMergeTarget(anyString(), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(Optional.of(EXISTING_EVENT_ID));
    }

    private void givenNoMergeTarget() {
        when(disasterEventRepository.findRegionalTypeMergeTarget(anyString(), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
    }

    private void verifyEmbeddingPathSkipped() {
        verify(embeddingModel, never()).embed(anyString());
        verify(alertEmbeddingRepository, never()).findEmbeddingText(any());
        verify(disasterEventRepository, never())
                .findTopCandidates(anyString(), any(String[].class), any(LocalDateTime.class));
        verify(disasterEventRepository, never())
                .findAdvisoryMergeTarget(anyString(), any(String[].class), any(LocalDateTime.class));
    }

    private void verifyEmbeddingPathTaken() {
        verify(disasterEventRepository)
                .findTopCandidates(anyString(), any(String[].class), any(LocalDateTime.class));
        verify(disasterEventRepository, never())
                .findRegionalTypeMergeTarget(anyString(), any(String[].class), any(LocalDateTime.class));
    }

    private void assertMergedRegionalType(long eventId) {
        ArgumentCaptor<EventAlertMapping> mapping = ArgumentCaptor.forClass(EventAlertMapping.class);
        verify(eventAlertMappingRepository).save(mapping.capture());
        assertThat(mapping.getValue().getMergeMethod()).isEqualTo(MergeMethod.REGIONAL_TYPE);
        assertThat(mapping.getValue().getSequenceNo()).isEqualTo(4);
        verify(disasterEventRepository).incrementOnMerge(eventId, now);
        verify(eventPublisher).publishEvent(new AlertClusteredEvent(eventId, 1L));
        verify(disasterEventRepository, never()).save(any(DisasterEvent.class));
    }

    @Test
    @DisplayName("1) 폭염 단일 시군구: 같은 유형+시군구 이벤트가 있으면 임베딩 없이 REGIONAL_TYPE 머지 + incrementOnMerge + 이벤트 발행")
    void heat_mergesIntoExistingEvent() {
        alert("폭염", HEAT_WARNING, REGION);
        givenMergeTarget();

        service.clusterNewAlert(1L);

        verifyEmbeddingPathSkipped();
        assertMergedRegionalType(EXISTING_EVENT_ID);
    }

    @Test
    @DisplayName("2) 대상이 없으면 비안내 신규 이벤트 + SEED 매핑 + AlertClusteredEvent 발행, 임베딩 미호출")
    void heat_createsNewNonAdvisoryEventWhenNoTarget() {
        alert("폭염", HEAT_WARNING, REGION);
        givenNoMergeTarget();

        service.clusterNewAlert(1L);

        verifyEmbeddingPathSkipped();
        verify(disasterEventRepository).findRegionalTypeMergeTarget(
                eq("폭염"), any(String[].class), any(LocalDateTime.class));
        ArgumentCaptor<DisasterEvent> event = ArgumentCaptor.forClass(DisasterEvent.class);
        verify(disasterEventRepository).save(event.capture());
        assertThat(event.getValue().isAdvisory()).isFalse();
        assertThat(event.getValue().getPrimaryDisasterType()).isEqualTo("폭염");
        ArgumentCaptor<EventAlertMapping> mapping = ArgumentCaptor.forClass(EventAlertMapping.class);
        verify(eventAlertMappingRepository).save(mapping.capture());
        assertThat(mapping.getValue().getMergeMethod()).isEqualTo(MergeMethod.SEED);
        verify(eventPublisher).publishEvent(new AlertClusteredEvent(900L, 1L));
    }

    @Test
    @DisplayName("3a) since 는 알림 시각 - 168h(기본), 시군구 인자는 앞 5자(44800)")
    void sinceIsCreatedAtMinusDefaultWindow() {
        alert("폭염", HEAT_WARNING, REGION);
        givenNoMergeTarget();

        service.clusterNewAlert(1L);

        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(disasterEventRepository).findRegionalTypeMergeTarget(
                eq("폭염"), eq(new String[]{"44800"}), since.capture());
        assertThat(since.getValue()).isEqualTo(now.minusHours(168));
    }

    @Test
    @DisplayName("3b) episodicWindowHours=72 로 바꾸면 since 는 알림 시각 - 72h")
    void sinceFollowsConfiguredWindow() {
        ReflectionTestUtils.setField(service, "episodicWindowHours", 72);
        alert("폭염", HEAT_WARNING, REGION);
        givenNoMergeTarget();

        service.clusterNewAlert(1L);

        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(disasterEventRepository).findRegionalTypeMergeTarget(
                eq("폭염"), eq(new String[]{"44800"}), since.capture());
        assertThat(since.getValue()).isEqualTo(now.minusHours(72));
    }

    @ParameterizedTest(name = "4) 본문과 무관: \"{0}\" 도 같은 조회로 머지 경로를 탄다")
    @ValueSource(strings = {HEAT_GUIDE, HEAT_WARNING})
    void messageContentIsIgnored(String message) {
        alert("폭염", message, REGION);
        givenMergeTarget();

        service.clusterNewAlert(1L);

        verify(disasterEventRepository).findRegionalTypeMergeTarget(
                eq("폭염"), eq(new String[]{"44800"}), eq(now.minusHours(168)));
        verifyEmbeddingPathSkipped();
        assertMergedRegionalType(EXISTING_EVENT_ID);
    }

    @ParameterizedTest(name = "5) 에피소드형 유형 {0}: 키 병합되어 머지")
    @CsvSource(delimiter = '|', value = {
            "폭염|폭염경보 발효, 야외활동 자제 바랍니다.[홍성군]",
            "한파|한파경보 발효, 보온에 유의 바랍니다.[홍성군]",
            "대설|대설주의보 발효 중, 차량 운행에 주의 바랍니다.[홍성군]",
            "강풍|강풍주의보 발효 중, 시설물 관리에 유의 바랍니다.[홍성군]",
            "가축질병|구제역 발생, 축산농가 이동 제한 및 소독 바랍니다.[홍성군]"
    })
    void allEpisodicTypesAreAnchored(String type, String message) {
        alert(type, message, REGION);
        givenMergeTarget();

        service.clusterNewAlert(1L);

        verify(disasterEventRepository).findRegionalTypeMergeTarget(
                eq(type), eq(new String[]{"44800"}), eq(now.minusHours(168)));
        verifyEmbeddingPathSkipped();
        assertMergedRegionalType(EXISTING_EVENT_ID);
    }

    @Test
    @DisplayName("5b) episodicTypesCsv 의 공백(\"폭염, 한파\")은 trim 되어 한파도 키 병합된다")
    void episodicTypesCsv_trimsWhitespace() {
        ReflectionTestUtils.setField(service, "episodicTypesCsv", "폭염, 한파");
        alert("한파", "한파경보 발효, 보온에 유의 바랍니다.[홍성군]", REGION);
        givenMergeTarget();

        service.clusterNewAlert(1L);

        verifyEmbeddingPathSkipped();
        assertMergedRegionalType(EXISTING_EVENT_ID);
    }

    // ===== 다지역 에피소드형 → broadcast (시군구 간 다리 차단) =====

    private static final String[] SAME_SIDO_CODES = {"4375000000", "4376000000", "4377000000"}; // 충북 3개 시군구
    private static final String[] WIDE_CODES = {"1111000000", "1114000000", "1117000000", "1120000000", "1121000000",
            "1123000000", "1126000000", "1129000000", "1130000000", "1132000000", "1135000000"}; // 서울 11개 > 10
    private static final String[] NATIONWIDE_CODES = {"1111000000", "2611000000", "2711000000", "2811000000",
            "3011000000", "3111000000", "3611000000", "4111000000"}; // 8개 시도 >= nationwideSidoSpan(8)
    private static final String[] SIDO_WIDE_CODES = {"1111000000", "1114000000", "4111000000"}; // 서울 2 + 경기 1

    private DisasterEvent broadcastEvent(long id) {
        return DisasterEvent.builder().id(id).primaryDisasterType("폭염").broadcast(true).build();
    }

    private void givenSidoBroadcastTarget() {
        when(disasterEventRepository
                .findLatestGeneralBroadcastBySido(
                        anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.of(broadcastEvent(EXISTING_EVENT_ID)));
    }

    private void givenNationwideBroadcastTarget() {
        when(disasterEventRepository
                .findLatestGeneralBroadcastNationwide(
                        anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.of(broadcastEvent(EXISTING_EVENT_ID)));
    }

    /** 지역 이벤트 후보 조회·키 병합 대상 조회·임베딩이 전혀 일어나지 않았음 — 지역 이벤트 footprint 를 넓힐 경로가 없다. */
    private void verifyLocalPathsUntouched() {
        verifyEmbeddingPathSkipped();
        verify(disasterEventRepository, never())
                .findRegionalTypeMergeTarget(anyString(), any(String[].class), any(LocalDateTime.class));
    }

    private void assertMergedBroadcast(long eventId) {
        ArgumentCaptor<EventAlertMapping> mapping = ArgumentCaptor.forClass(EventAlertMapping.class);
        verify(eventAlertMappingRepository).save(mapping.capture());
        assertThat(mapping.getValue().getMergeMethod()).isEqualTo(MergeMethod.BROADCAST);
        verify(disasterEventRepository).incrementOnMerge(eventId, now);
        verify(eventPublisher).publishEvent(new AlertClusteredEvent(eventId, 1L));
        verify(disasterEventRepository, never()).save(any(DisasterEvent.class));
    }

    private DisasterEvent assertSavedOnlyBroadcastEvent() {
        ArgumentCaptor<DisasterEvent> event = ArgumentCaptor.forClass(DisasterEvent.class);
        verify(disasterEventRepository).save(event.capture());
        assertThat(event.getValue().isBroadcast()).isTrue();
        return event.getValue();
    }

    @ParameterizedTest(name = "6) 다지역 에피소드형(폭염) 시군구 {0}개 + 같은 시도 broadcast 이벤트 있음: 임베딩·지역 조회 없이 BROADCAST 머지")
    @ValueSource(ints = {2, 3})
    void multiRegionEpisodic_mergesIntoBroadcastEvent(int count) {
        alert("폭염", HEAT_WARNING, java.util.Arrays.copyOf(SAME_SIDO_CODES, count));
        givenSidoBroadcastTarget();
        givenMergeTarget();

        service.clusterNewAlert(1L);

        verifyLocalPathsUntouched();
        verify(disasterEventRepository)
                .findLatestGeneralBroadcastBySido(
                        eq("폭염"), eq("43"), eq(now.minusHours(168)));
        assertMergedBroadcast(EXISTING_EVENT_ID);
    }

    @ParameterizedTest(name = "6a) 다지역 에피소드형(폭염) 시군구 {0}개 + broadcast 이벤트 없음: 임베딩·지역 조회 없이 신규 broadcast 이벤트")
    @ValueSource(ints = {2, 3})
    void multiRegionEpisodic_createsNewBroadcastEvent(int count) {
        alert("폭염", HEAT_WARNING, java.util.Arrays.copyOf(SAME_SIDO_CODES, count));
        givenMergeTarget();
        when(disasterEventRepository
                .findLatestGeneralBroadcastBySido(
                        anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        service.clusterNewAlert(1L);

        verifyLocalPathsUntouched();
        assertSavedOnlyBroadcastEvent();
        verify(disasterEventRepository, never()).incrementOnMerge(any(), any());
    }

    @Test
    @DisplayName("6b) 광역(11개 시군구 > maxRegionSpan) 에피소드형도 broadcast - 새 에피소드형 단계가 처리해 임베딩·후보 조회 미호출")
    void wideRegionEpisodic_goesBroadcastPath() {
        alert("폭염", HEAT_WARNING, WIDE_CODES);
        givenMergeTarget();

        service.clusterNewAlert(1L);

        verifyLocalPathsUntouched();
        assertSavedOnlyBroadcastEvent();
    }

    @Test
    @DisplayName("6c) 시도 span >= nationwideSidoSpan 인 다지역 에피소드형은 '전국' broadcast - 유형만 키, primary_region_code null")
    void nationwideEpisodic_goesNationwideBroadcast() {
        alert("폭염", HEAT_WARNING, NATIONWIDE_CODES);
        givenNationwideBroadcastTarget();
        givenMergeTarget();

        service.clusterNewAlert(1L);

        verifyLocalPathsUntouched();
        verify(disasterEventRepository)
                .findLatestGeneralBroadcastNationwide(
                        eq("폭염"), eq(now.minusHours(168)));
        assertMergedBroadcast(EXISTING_EVENT_ID);
    }

    @Test
    @DisplayName("6d) 전국 broadcast 대상이 없으면 primary_region_code null, 라벨 '전국' 의 신규 broadcast 이벤트")
    void nationwideEpisodic_createsNewNationwideBroadcast() {
        alert("폭염", HEAT_WARNING, NATIONWIDE_CODES);
        when(disasterEventRepository
                .findLatestGeneralBroadcastNationwide(
                        anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        service.clusterNewAlert(1L);

        verifyLocalPathsUntouched();
        DisasterEvent saved = assertSavedOnlyBroadcastEvent();
        assertThat(saved.getPrimaryRegionCode()).isNull();
        assertThat(saved.getPrimaryRegionName()).isEqualTo("전국");
    }

    @Test
    @DisplayName("6e) 시도 span 이 nationwideSidoSpan 미만이면 시도 광역 - 최다 시군구 시도(서울=11) 키로 조회")
    void sidoWideEpisodic_usesDominantSidoKey() {
        alert("폭염", HEAT_WARNING, SIDO_WIDE_CODES);
        givenSidoBroadcastTarget();
        givenMergeTarget();

        service.clusterNewAlert(1L);

        verifyLocalPathsUntouched();
        verify(disasterEventRepository)
                .findLatestGeneralBroadcastBySido(
                        eq("폭염"), eq("11"), eq(now.minusHours(168)));
        verify(disasterEventRepository, never())
                .findLatestGeneralBroadcastNationwide(
                        anyString(), any(LocalDateTime.class));
        assertMergedBroadcast(EXISTING_EVENT_ID);
    }

    @ParameterizedTest(name = "6f) 에피소드형 유형 {0} 도 다지역이면 broadcast 로 간다")
    @CsvSource(delimiter = '|', value = {
            "폭염|폭염경보 발효, 야외활동 자제 바랍니다.",
            "한파|한파경보 발효, 보온에 유의 바랍니다.",
            "대설|대설주의보 발효 중, 차량 운행에 주의 바랍니다.",
            "강풍|강풍주의보 발효 중, 시설물 관리에 유의 바랍니다.",
            "가축질병|구제역 발생, 축산농가 이동 제한 및 소독 바랍니다."
    })
    void allEpisodicTypes_multiRegionGoBroadcast(String type, String message) {
        alert(type, message, SAME_SIDO_CODES);
        givenMergeTarget();

        service.clusterNewAlert(1L);

        verifyLocalPathsUntouched();
        assertSavedOnlyBroadcastEvent();
    }

    @Test
    @DisplayName("6g) 다지역 에피소드형은 지역 이벤트를 넓히지 않는다 - 후보·키 병합 대상 조회가 없어 REGIONAL_TYPE/EMBEDDING 머지 불가, 저장되는 이벤트는 broadcast 뿐")
    void multiRegionEpisodic_neverWidensLocalEvent() {
        alert("폭염", HEAT_WARNING, SAME_SIDO_CODES);
        givenMergeTarget();
        when(disasterEventRepository.findTopCandidates(anyString(), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(java.util.Collections.singletonList(new Object[]{EXISTING_EVENT_ID, 0.01}));

        service.clusterNewAlert(1L);

        verifyLocalPathsUntouched();
        verify(eventAlertMappingRepository, never()).save(org.mockito.ArgumentMatchers.argThat(
                m -> m.getMergeMethod() == MergeMethod.EMBEDDING || m.getMergeMethod() == MergeMethod.REGIONAL_TYPE));
        verify(disasterEventRepository, never()).incrementOnMerge(eq(EXISTING_EVENT_ID), any());
        assertSavedOnlyBroadcastEvent();
    }

    @ParameterizedTest(name = "6h) 사고성 유형(화재) 다지역 {0}개는 전파 허용 - 기존 임베딩 경로, broadcast 아님 [현재 동작과 같아 통과]")
    @ValueSource(ints = {2, 3})
    void accidentMultiRegion_staysOnEmbeddingPath(int count) {
        alert("화재", "공장 화재 발생, 인근 주민은 대피 바랍니다.", java.util.Arrays.copyOf(SAME_SIDO_CODES, count));
        givenMergeTarget();

        service.clusterNewAlert(1L);

        verifyEmbeddingPathTaken();
        ArgumentCaptor<DisasterEvent> event = ArgumentCaptor.forClass(DisasterEvent.class);
        verify(disasterEventRepository).save(event.capture());
        assertThat(event.getValue().isBroadcast()).isFalse();
    }

    @ParameterizedTest(name = "6i) 비대상 유형(호우) 다지역 {0}개도 기존 임베딩 경로 [현재 동작과 같아 통과]")
    @ValueSource(ints = {2, 3})
    void nonEpisodicMultiRegion_staysOnEmbeddingPath(int count) {
        alert("호우", "호우주의보 발효 중, 하천 접근을 자제 바랍니다.", java.util.Arrays.copyOf(SAME_SIDO_CODES, count));
        givenMergeTarget();

        service.clusterNewAlert(1L);

        verifyEmbeddingPathTaken();
        ArgumentCaptor<DisasterEvent> event = ArgumentCaptor.forClass(DisasterEvent.class);
        verify(disasterEventRepository).save(event.capture());
        assertThat(event.getValue().isBroadcast()).isFalse();
    }

    @Test
    @DisplayName("6j) episodicTypesCsv 가 빈 문자열이면 폭염 다지역도 기존 임베딩 경로 [현재 동작과 같아 통과]")
    void emptyEpisodicTypes_multiRegionHeatGoesEmbeddingPath() {
        ReflectionTestUtils.setField(service, "episodicTypesCsv", "");
        alert("폭염", HEAT_WARNING, SAME_SIDO_CODES);
        givenMergeTarget();

        service.clusterNewAlert(1L);

        verifyEmbeddingPathTaken();
        ArgumentCaptor<DisasterEvent> event = ArgumentCaptor.forClass(DisasterEvent.class);
        verify(disasterEventRepository).save(event.capture());
        assertThat(event.getValue().isBroadcast()).isFalse();
    }

    @ParameterizedTest(name = "7) 대상이 아닌 유형 {0}: 키 병합하지 않고 기존 경로 [현재 동작과 같아 뼈대도 통과]")
    @CsvSource(delimiter = '|', value = {
            "호우|호우주의보 발효 중, 하천 접근을 자제 바랍니다.[홍성군]",
            "화재|홍성군 OO동 공장 화재 발생, 인근 주민은 대피 바랍니다."
    })
    void nonEpisodicType_goesEmbeddingPath(String type, String message) {
        alert(type, message, REGION);
        givenMergeTarget();

        service.clusterNewAlert(1L);

        verifyEmbeddingPathTaken();
    }

    @Test
    @DisplayName("8) episodicTypesCsv 가 빈 문자열이면 폭염도 키 병합되지 않고 임베딩 경로 [현재 동작과 같아 뼈대도 통과]")
    void emptyEpisodicTypes_heatGoesEmbeddingPath() {
        ReflectionTestUtils.setField(service, "episodicTypesCsv", "");
        alert("폭염", HEAT_WARNING, REGION);
        givenMergeTarget();

        service.clusterNewAlert(1L);

        verifyEmbeddingPathTaken();
    }

    @ParameterizedTest(name = "9) 폭염 \"{0}\": 안내성 분리로 가지 않는다 - findAdvisoryMergeTarget 미호출, 비안내 이벤트, 키 병합 대상 조회 1회")
    @ValueSource(strings = {HEAT_GUIDE, HEAT_WARNING})
    void heat_noLongerSplitAsAdvisory(String message) {
        alert("폭염", message, REGION);
        givenNoMergeTarget();

        service.clusterNewAlert(1L);

        verify(disasterEventRepository, never())
                .findAdvisoryMergeTarget(anyString(), any(String[].class), any(LocalDateTime.class));
        verify(disasterEventRepository).findRegionalTypeMergeTarget(
                eq("폭염"), any(String[].class), any(LocalDateTime.class));
        ArgumentCaptor<DisasterEvent> event = ArgumentCaptor.forClass(DisasterEvent.class);
        verify(disasterEventRepository).save(event.capture());
        assertThat(event.getValue().isAdvisory()).isFalse();
    }

    @ParameterizedTest(name = "9b) 옛 설정(advisory-split-types=산불,폭염,한파)이 남아 있어도 {0} 은 안내성 분리로 가지 않는다")
    @CsvSource(delimiter = '|', value = {
            "폭염|무더위가 지속되고 있습니다. ▲야외 활동 자제 ▲무더위쉼터 이용[홍성군]",
            "한파|강한 한파 지속 ▲방한용품 착용 ▲한랭질환 주의 ... 안전에 유의 [홍성군]"
    })
    void legacyAdvisorySplitConfig_isIgnoredForHeatAndCold(String type, String message) {
        ReflectionTestUtils.setField(service, "advisorySplitTypesCsv", "산불,폭염,한파");
        alert(type, message, REGION);
        givenNoMergeTarget();

        service.clusterNewAlert(1L);

        verify(disasterEventRepository, never())
                .findAdvisoryMergeTarget(anyString(), any(String[].class), any(LocalDateTime.class));
        verify(disasterEventRepository).findRegionalTypeMergeTarget(
                eq(type), any(String[].class), any(LocalDateTime.class));
        ArgumentCaptor<DisasterEvent> event = ArgumentCaptor.forClass(DisasterEvent.class);
        verify(disasterEventRepository).save(event.capture());
        assertThat(event.getValue().isAdvisory()).isFalse();
    }

    @Test
    @DisplayName("10) 지역코드가 없는 알림은 키 병합 대상이 아니다 - 기존 임베딩 경로의 '지역 없음' 신규 이벤트 [현재 동작과 같아 뼈대도 통과]")
    void noRegion_isNotAnchored() {
        alert("폭염", HEAT_WARNING);
        givenMergeTarget();

        service.clusterNewAlert(1L);

        verify(disasterEventRepository, never())
                .findRegionalTypeMergeTarget(anyString(), any(String[].class), any(LocalDateTime.class));
        verify(disasterEventRepository, never())
                .findTopCandidates(anyString(), any(String[].class), any(LocalDateTime.class));
        verify(disasterEventRepository).save(any(DisasterEvent.class));
        verify(disasterEventRepository, never()).incrementOnMerge(any(), any());
    }
}
