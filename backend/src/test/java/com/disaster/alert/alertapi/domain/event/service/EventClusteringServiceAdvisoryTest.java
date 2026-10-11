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
import static org.mockito.Mockito.when;

/**
 * 안내성 분리를 산불 외 폭염·한파로 확장한 라우팅 검증.
 *
 * <p>가정: 레포 메서드 {@code findFireAdvisoryMergeTarget} 을 {@code findAdvisoryMergeTarget(type, sigunguCodes, since)}
 * 로 일반화. 머지 윈도우는 별도 설정값이 아니라 유형별 {@code DisasterCooldown.hoursFor}(폭염·한파 168h)이고,
 * 산불 안내는 기존 regional-types 윈도우(336h)를 그대로 쓴다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventClusteringServiceAdvisoryTest {

    private static final String HEAT_GUIDE =
            "오늘 무더위가 예상됩니다.▲낮 시간 논·밭작업 및 야외활동 자제▲무더위쉼터 이용"
                    + "▲폭염안전수칙(물,그늘,휴식)을 준수하여 건강관리에 유의하시기 바랍니다.[홍성군]";
    private static final String COLD_GUIDE =
            "강한 한파 지속 ▲방한용품 착용 ▲한랭질환 주의 ... 안전에 유의 [영동군]";
    private static final String HEAT_WARNING = "폭염경보 발효, 야외활동 자제 바랍니다.[홍성군]";

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
        // 폭염·한파는 regional-types 에 넣지 않는다(매일 발령 -> 윈도우 체인 blob).
        ReflectionTestUtils.setField(service, "regionalTypesCsv", "산불:336,산사태:168,홍수:168");
        ReflectionTestUtils.setField(service, "advisorySplitTypesCsv", "산불,폭염,한파");

        when(disasterEventRepository.save(any(DisasterEvent.class))).thenAnswer(inv -> {
            DisasterEvent e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", 900L);
            return e;
        });
        when(alertEmbeddingRepository.findEmbeddingText(1L)).thenReturn("[0.1,0.2]");
        when(disasterEventRepository.findTopCandidates(anyString(), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(List.of());
    }

    private DisasterAlert alert(String type, String message, DisasterLevel level, String... regionCodes) {
        DisasterAlert a = DisasterAlert.builder()
                .sn(100L).message(message).createdAt(now).disasterType(type).emergencyLevel(level).build();
        ReflectionTestUtils.setField(a, "id", 1L);
        for (String code : regionCodes) {
            a.addRegionCode(code);
        }
        when(disasterAlertRepository.findById(1L)).thenReturn(Optional.of(a));
        return a;
    }

    private void verifyEmbeddingPathSkipped() {
        verify(embeddingModel, never()).embed(anyString());
        verify(disasterEventRepository, never())
                .findTopCandidates(anyString(), any(String[].class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("폭염 안내: 같은 시군구 안내 이벤트가 윈도우 안에 있으면 임베딩 없이 ADVISORY 머지 + incrementOnMerge + 이벤트 발행")
    void heatAdvisory_mergesIntoExistingAdvisoryEvent() {
        alert("폭염", HEAT_GUIDE, DisasterLevel.LEVEL_1, "4480000000");
        when(disasterEventRepository.findAdvisoryMergeTarget(eq("폭염"), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(Optional.of(55L));
        when(eventAlertMappingRepository.countByEventId(55L)).thenReturn(3);

        service.clusterNewAlert(1L);

        verifyEmbeddingPathSkipped();
        ArgumentCaptor<EventAlertMapping> mapping = ArgumentCaptor.forClass(EventAlertMapping.class);
        verify(eventAlertMappingRepository).save(mapping.capture());
        assertThat(mapping.getValue().getMergeMethod()).isEqualTo(MergeMethod.ADVISORY);
        assertThat(mapping.getValue().getSequenceNo()).isEqualTo(4);
        verify(disasterEventRepository).incrementOnMerge(55L, now);
        verify(eventPublisher).publishEvent(new AlertClusteredEvent(55L, 1L));
    }

    @Test
    @DisplayName("폭염 안내: 대상이 없으면 createAdvisory 로 신규 안내 이벤트 + SEED 매핑 + AlertClusteredEvent 발행")
    void heatAdvisory_createsNewAdvisoryEventWhenNoTarget() {
        alert("폭염", HEAT_GUIDE, DisasterLevel.LEVEL_1, "4480000000");
        when(disasterEventRepository.findAdvisoryMergeTarget(anyString(), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        service.clusterNewAlert(1L);

        verifyEmbeddingPathSkipped();
        ArgumentCaptor<DisasterEvent> event = ArgumentCaptor.forClass(DisasterEvent.class);
        verify(disasterEventRepository).save(event.capture());
        assertThat(event.getValue().isAdvisory()).isTrue();
        assertThat(event.getValue().getPrimaryDisasterType()).isEqualTo("폭염");
        ArgumentCaptor<EventAlertMapping> mapping = ArgumentCaptor.forClass(EventAlertMapping.class);
        verify(eventAlertMappingRepository).save(mapping.capture());
        assertThat(mapping.getValue().getMergeMethod()).isEqualTo(MergeMethod.SEED);
        verify(eventPublisher).publishEvent(new AlertClusteredEvent(900L, 1L));
    }

    @Test
    @DisplayName("안내 윈도우: 폭염 since 는 알림 시각 - DisasterCooldown.hoursFor(폭염)=168h 로 조회한다")
    void heatAdvisory_usesCooldownWindow168h() {
        alert("폭염", HEAT_GUIDE, DisasterLevel.LEVEL_1, "4480000000");
        when(disasterEventRepository.findAdvisoryMergeTarget(anyString(), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        service.clusterNewAlert(1L);

        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(disasterEventRepository).findAdvisoryMergeTarget(eq("폭염"), eq(new String[]{"44800"}), since.capture());
        assertThat(since.getValue()).isEqualTo(now.minusHours(168));
    }

    @Test
    @DisplayName("안내 윈도우: 한파도 168h, 윈도우 밖(레포가 대상 없음 반환)이면 신규 안내 이벤트를 만든다")
    void coldAdvisory_usesCooldownWindow168h_andCreatesNewWhenOutside() {
        alert("한파", COLD_GUIDE, DisasterLevel.LEVEL_1, "4375000000");
        when(disasterEventRepository.findAdvisoryMergeTarget(anyString(), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        service.clusterNewAlert(1L);

        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(disasterEventRepository).findAdvisoryMergeTarget(eq("한파"), any(String[].class), since.capture());
        assertThat(since.getValue()).isEqualTo(now.minusHours(168));
        verify(disasterEventRepository).save(any(DisasterEvent.class));
        verify(disasterEventRepository, never()).incrementOnMerge(any(), any());
    }

    @Test
    @DisplayName("폭염이라도 특보 발효 문구는 안내 경로를 타지 않고 기존 임베딩 후보 검색 경로로 간다")
    void heatWarning_goesThroughEmbeddingPath() {
        alert("폭염", HEAT_WARNING, DisasterLevel.LEVEL_1, "4480000000");
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});

        service.clusterNewAlert(1L);

        verify(disasterEventRepository).findTopCandidates(anyString(), any(String[].class), any(LocalDateTime.class));
        verify(disasterEventRepository, never())
                .findAdvisoryMergeTarget(anyString(), any(String[].class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("광역(span > maxRegionSpan) 폭염 안내는 안내 경로가 아니라 기존 broadcast 경로다")
    void wideHeatAdvisory_goesBroadcastPath() {
        String[] codes = {"1111000000", "1114000000", "1117000000", "1120000000", "1121000000", "1123000000",
                "1126000000", "1129000000", "1130000000", "1132000000", "1135000000"}; // 11개 시군구 > 10
        alert("폭염", HEAT_GUIDE, DisasterLevel.LEVEL_1, codes);

        service.clusterNewAlert(1L);

        verify(disasterEventRepository, never())
                .findAdvisoryMergeTarget(anyString(), any(String[].class), any(LocalDateTime.class));
        verify(disasterEventRepository, never())
                .findTopCandidates(anyString(), any(String[].class), any(LocalDateTime.class));
        ArgumentCaptor<DisasterEvent> event = ArgumentCaptor.forClass(DisasterEvent.class);
        verify(disasterEventRepository).save(event.capture());
        assertThat(event.getValue().isAdvisory()).isFalse();
        assertThat(event.getValue().isBroadcast()).isTrue();
    }

    @Test
    @DisplayName("advisorySplitTypes 에 폭염이 없으면(기존 기본값 산불) 폭염 안내도 기존처럼 임베딩 경로 - 회귀 방지")
    void heatAdvisory_withoutConfigStaysOnEmbeddingPath() {
        ReflectionTestUtils.setField(service, "advisorySplitTypesCsv", "산불");
        alert("폭염", HEAT_GUIDE, DisasterLevel.LEVEL_1, "4480000000");
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});

        service.clusterNewAlert(1L);

        verify(disasterEventRepository).findTopCandidates(anyString(), any(String[].class), any(LocalDateTime.class));
        verify(disasterEventRepository, never())
                .findAdvisoryMergeTarget(anyString(), any(String[].class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("한파 안내도 폭염과 같이 임베딩 없이 안내 경로(신규 안내 이벤트)로 간다")
    void coldAdvisory_createsAdvisoryEvent() {
        alert("한파", COLD_GUIDE, DisasterLevel.LEVEL_1, "4375000000");
        when(disasterEventRepository.findAdvisoryMergeTarget(eq("한파"), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        service.clusterNewAlert(1L);

        verifyEmbeddingPathSkipped();
        ArgumentCaptor<DisasterEvent> event = ArgumentCaptor.forClass(DisasterEvent.class);
        verify(disasterEventRepository).save(event.capture());
        assertThat(event.getValue().isAdvisory()).isTrue();
        assertThat(event.getValue().getPrimaryDisasterType()).isEqualTo("한파");
        verify(eventPublisher).publishEvent(new AlertClusteredEvent(900L, 1L));
    }

    @Test
    @DisplayName("산불 안내 경로도 일반화된 findAdvisoryMergeTarget 을 쓴다(산불은 기존 regional 윈도우 336h)")
    void fireAdvisory_usesGeneralizedMergeTarget() {
        alert("산불", "건조특보 발효 중, 산림 인접 소각 금지 및 산불 예방에 협조 바랍니다",
                DisasterLevel.LEVEL_1, "4480000000");
        when(disasterEventRepository.findAdvisoryMergeTarget(eq("산불"), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(Optional.of(66L));
        when(eventAlertMappingRepository.countByEventId(66L)).thenReturn(1);

        service.clusterNewAlert(1L);

        verify(disasterEventRepository).findAdvisoryMergeTarget(
                eq("산불"), eq(new String[]{"44800"}), eq(now.minusHours(336)));
        verify(disasterEventRepository).incrementOnMerge(66L, now);
    }
}
