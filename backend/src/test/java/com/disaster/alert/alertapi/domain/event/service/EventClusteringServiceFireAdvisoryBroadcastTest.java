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
import static org.mockito.Mockito.when;

/**
 * 산불 안내의 다지역 광역 분리 검증.
 *
 * <p>산불 안내가 시군구 수와 무관하게 clusterAdvisory(시군구 footprint 교집합)로 가면 다지역 안내가 이웃
 * 시군구 안내 이벤트를 이어 붙인다(평가 DB: 알림 360건·148일·29개 시군구 이벤트). 시군구 2개 이상(또는
 * maxRegionSpan 초과) 산불 안내는 시도(전국)+유형 안내 광역 이벤트로 보내고 윈도우는 산불 유형별 336h 를 쓴다.
 * 순수 Mockito 단위 테스트(DB·Spring 컨텍스트 없음).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventClusteringServiceFireAdvisoryBroadcastTest {

    private static final long EXISTING_EVENT_ID = 77L;
    private static final String[] SAME_SIDO_CODES = {"4711000000", "4713000000", "4715000000"}; // 경북 3개 시군구
    private static final String[] NATIONWIDE_CODES = {
            "1111000000", "2611000000", "2711000000", "2811000000",
            "3011000000", "3111000000", "4111000000", "4311000000"};

    private static final String FIRE_ADVISORY =
            "작은 불씨도 산불로 번질 수 있습니다. 입산시 화기소지 금지, 영농부산물·쓰레기 소각금지, "
                    + "화목보일러 불씨 소화 확인 등 각별히 주의 바랍니다. [경상북도]";
    private static final String FIRE_INCIDENT =
            "오늘 09:11 양동면 고송리 1339-5번지 일원 산불 발생. 입산금지.";
    private static final String WATER_GUIDE =
            "수상안전사고 예방을 위해 ▲구명조끼 착용 ▲위험구역 진입금지 ▲야간, 음주수영 금지 ▲어린이는 반드시 보호자 동반 등 "
                    + "안전수칙을 준수하시기 바랍니다. [경상북도]";

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

    private static String[] sigunguCodes(int count) {
        String[] codes = new String[count];
        for (int i = 0; i < count; i++) {
            codes[i] = "47" + (110 + i * 10) + "00000";
        }
        return codes;
    }

    private DisasterEvent existingAdvisoryBroadcast(String type) {
        DisasterEvent e = DisasterEvent.createAdvisory(type, "4711000000", "경상북도", now.minusHours(10));
        ReflectionTestUtils.setField(e, "id", EXISTING_EVENT_ID);
        return e;
    }

    private void verifyAdvisoryBroadcastNeverQueried() {
        verify(disasterEventRepository, never())
                .findAdvisoryBroadcastBySido(anyString(), anyString(), any(LocalDateTime.class));
        verify(disasterEventRepository, never())
                .findAdvisoryBroadcastNationwide(anyString(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("a) 산불 안내 + 시군구 3개 + 같은 시도 안내 광역 이벤트 있음: 336h 윈도우로 BROADCAST 병합, 시군구 안내 조회·신규 저장 없음")
    void fireAdvisoryMultiRegion_mergesIntoSidoAdvisoryBroadcast() {
        alert("산불", FIRE_ADVISORY, DisasterLevel.LEVEL_1, SAME_SIDO_CODES);
        when(disasterEventRepository.findAdvisoryBroadcastBySido(anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.of(existingAdvisoryBroadcast("산불")));

        service.clusterNewAlert(1L);

        verify(disasterEventRepository).findAdvisoryBroadcastBySido(eq("산불"), eq("47"), eq(now.minusHours(336)));
        verify(disasterEventRepository, never())
                .findAdvisoryMergeTarget(anyString(), any(String[].class), any(LocalDateTime.class));
        ArgumentCaptor<EventAlertMapping> mapping = ArgumentCaptor.forClass(EventAlertMapping.class);
        verify(eventAlertMappingRepository).save(mapping.capture());
        assertThat(mapping.getValue().getMergeMethod()).isEqualTo(MergeMethod.BROADCAST);
        verify(disasterEventRepository).incrementOnMerge(EXISTING_EVENT_ID, now);
        verify(disasterEventRepository, never()).save(any(DisasterEvent.class));
    }

    @Test
    @DisplayName("b) 산불 안내 + 시군구 3개 + 대상 없음: 신규 이벤트가 is_advisory && is_broadcast, primaryRegionCode 는 해당 시도의 첫 코드")
    void fireAdvisoryMultiRegion_createsAdvisoryBroadcastEvent() {
        alert("산불", FIRE_ADVISORY, DisasterLevel.LEVEL_1, SAME_SIDO_CODES);

        service.clusterNewAlert(1L);

        ArgumentCaptor<DisasterEvent> event = ArgumentCaptor.forClass(DisasterEvent.class);
        verify(disasterEventRepository).save(event.capture());
        assertThat(event.getValue().isAdvisory()).isTrue();
        assertThat(event.getValue().isBroadcast()).isTrue();
        assertThat(event.getValue().getPrimaryRegionCode()).isEqualTo("4711000000");
    }

    @Test
    @DisplayName("c) 산불 안내 + 시군구 11개(> maxRegionSpan): 일반 광역 finder 대신 안내 광역 finder (336h)")
    void fireAdvisoryOverMaxSpan_usesAdvisoryBroadcast() {
        alert("산불", FIRE_ADVISORY, DisasterLevel.LEVEL_1, sigunguCodes(11));

        service.clusterNewAlert(1L);

        verify(disasterEventRepository).findAdvisoryBroadcastBySido(eq("산불"), eq("47"), eq(now.minusHours(336)));
        verify(disasterEventRepository, never())
                .findFirstByBroadcastTrueAndPrimaryDisasterTypeAndPrimaryRegionCodeStartingWithAndLastAlertAtAfterOrderByLastAlertAtDesc(
                        anyString(), anyString(), any(LocalDateTime.class));
        verify(disasterEventRepository, never())
                .findFirstByBroadcastTrueAndPrimaryDisasterTypeAndPrimaryRegionCodeIsNullAndLastAlertAtAfterOrderByLastAlertAtDesc(
                        anyString(), any(LocalDateTime.class));
        ArgumentCaptor<DisasterEvent> event = ArgumentCaptor.forClass(DisasterEvent.class);
        verify(disasterEventRepository).save(event.capture());
        assertThat(event.getValue().isAdvisory()).isTrue();
        assertThat(event.getValue().isBroadcast()).isTrue();
    }

    @Test
    @DisplayName("d) 산불 안내 + 시도 8개 이상(전국급): 시도 키가 아니라 전국 안내 광역 finder (336h)")
    void fireAdvisoryNationwide_usesNationwideAdvisoryBroadcast() {
        alert("산불", FIRE_ADVISORY, DisasterLevel.LEVEL_1, NATIONWIDE_CODES);
        when(disasterEventRepository.findAdvisoryBroadcastNationwide(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.of(existingAdvisoryBroadcast("산불")));

        service.clusterNewAlert(1L);

        verify(disasterEventRepository).findAdvisoryBroadcastNationwide(eq("산불"), eq(now.minusHours(336)));
        verify(disasterEventRepository, never())
                .findAdvisoryBroadcastBySido(anyString(), anyString(), any(LocalDateTime.class));
        verify(disasterEventRepository, never()).save(any(DisasterEvent.class));
    }

    @Test
    @DisplayName("e) 산불 안내 + 시군구 1개: 기존대로 시군구 안내 머지(336h), 안내 광역 finder 미호출 [회귀 보호]")
    void fireAdvisorySingleRegion_staysSigunguAdvisory() {
        alert("산불", FIRE_ADVISORY, DisasterLevel.LEVEL_1, "4781000000");
        when(disasterEventRepository.findAdvisoryMergeTarget(anyString(), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(Optional.of(EXISTING_EVENT_ID));

        service.clusterNewAlert(1L);

        verify(disasterEventRepository).findAdvisoryMergeTarget(
                eq("산불"), eq(new String[]{"47810"}), eq(now.minusHours(336)));
        verifyAdvisoryBroadcastNeverQueried();
        ArgumentCaptor<EventAlertMapping> mapping = ArgumentCaptor.forClass(EventAlertMapping.class);
        verify(eventAlertMappingRepository).save(mapping.capture());
        assertThat(mapping.getValue().getMergeMethod()).isEqualTo(MergeMethod.ADVISORY);
    }

    @Test
    @DisplayName("f) 산불 사건(안내 아님) + 시군구 3개: 안내 광역 finder 미호출, 지역앵커 경로 [회귀 보호]")
    void fireIncidentMultiRegion_staysRegionalAnchor() {
        alert("산불", FIRE_INCIDENT, DisasterLevel.LEVEL_1, SAME_SIDO_CODES);

        service.clusterNewAlert(1L);

        verifyAdvisoryBroadcastNeverQueried();
        verify(disasterEventRepository).findRegionalTypeMergeTarget(
                eq("산불"), any(String[].class), eq(now.minusHours(336)));
    }

    @Test
    @DisplayName("g) 기타 물놀이 안내 다지역: 안내 광역 finder 를 advisoryWindowHours(168h)로 호출 [회귀 보호]")
    void etcAdvisoryMultiRegion_keepsAdvisoryWindow() {
        alert("기타", WATER_GUIDE, DisasterLevel.LEVEL_1, SAME_SIDO_CODES);

        service.clusterNewAlert(1L);

        verify(disasterEventRepository).findAdvisoryBroadcastBySido(eq("기타"), eq("47"), eq(now.minusHours(168)));
    }
}
