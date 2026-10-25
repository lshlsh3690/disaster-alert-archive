package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 지역 한정 유형(산불)의 인접 시군구 병합 검증.
 *
 * <p>대형 산불은 인접 시군구로 번지며 시군구별 이벤트로 쪼개진다(2025-03 경북: 의성·안동·청송·영양·영덕 5개).
 * 산불 알림이 같은 시군구에 이벤트가 없으면, 인접 시군구의 마지막 알림이 24시간 이내인 진행 중
 * 산불 이벤트에 병합한다. 순수 Mockito 단위 테스트(DB·Spring 컨텍스트 없음).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventClusteringServiceRegionalAdjacentTest {

    private static final long SAME_SIGUNGU_EVENT_ID = 55L;
    private static final long ADJACENT_EVENT_ID = 77L;
    private static final String SIGUNGU_CODE = "4781000000";

    private static final String FIRE_INCIDENT =
            "오늘 18:24 군북면 수곡리 320-4 산불 확산. 인근 주민은 대피바랍니다.";

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
        ReflectionTestUtils.setField(service, "regionalTypesCsv", "산불:168,산사태:168,홍수:168");
        ReflectionTestUtils.setField(service, "relaxedThresholdTypesCsv", "폭염,한파,대설,강풍,가축질병");
        ReflectionTestUtils.setField(service, "relaxedSimilarityThreshold", 0.65);
        ReflectionTestUtils.setField(service, "episodicTypesCsv", "폭염,한파,대설,강풍,가축질병");
        ReflectionTestUtils.setField(service, "episodicWindowHours", 168);
        ReflectionTestUtils.setField(service, "regionalAdjacentTypesCsv", "산불");
        ReflectionTestUtils.setField(service, "regionalAdjacentWindowHours", 24);

        when(disasterEventRepository.save(any(DisasterEvent.class))).thenAnswer(inv -> {
            DisasterEvent e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", 900L);
            return e;
        });
        when(alertEmbeddingRepository.findEmbeddingText(1L)).thenReturn("[0.1,0.2]");
        when(disasterEventRepository.findTopCandidates(anyString(), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(eventAlertMappingRepository.countByEventId(anyLong())).thenReturn(3);
        when(disasterEventRepository.findRegionalTypeMergeTarget(
                anyString(), any(String[].class), any(LocalDateTime.class))).thenReturn(Optional.empty());
        when(disasterEventRepository.findRegionalTypeAdjacentMergeTarget(
                anyString(), any(String[].class), any(LocalDateTime.class))).thenReturn(Optional.empty());
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

    private static String[] sigunguCodes(int count) {
        String[] codes = new String[count];
        for (int i = 0; i < count; i++) {
            codes[i] = "47" + (110 + i * 10) + "00000";
        }
        return codes;
    }

    private void stubAdjacentTarget() {
        when(disasterEventRepository.findRegionalTypeAdjacentMergeTarget(
                anyString(), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(Optional.of(ADJACENT_EVENT_ID));
    }

    private void verifyAdjacentNeverQueried() {
        verify(disasterEventRepository, never()).findRegionalTypeAdjacentMergeTarget(
                anyString(), any(String[].class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("a) 산불 사건 + 같은 시군구 대상 없음 + 인접 대상 있음: 인접 이벤트에 REGIONAL_TYPE 병합, 신규 저장 없음")
    void fireIncident_mergesIntoAdjacentEvent() {
        alert("산불", FIRE_INCIDENT, SIGUNGU_CODE);
        stubAdjacentTarget();

        service.clusterNewAlert(1L);

        verify(disasterEventRepository).findRegionalTypeAdjacentMergeTarget(
                eq("산불"), eq(new String[]{"47810"}), eq(now.minusHours(24)));
        ArgumentCaptor<EventAlertMapping> mapping = ArgumentCaptor.forClass(EventAlertMapping.class);
        verify(eventAlertMappingRepository).save(mapping.capture());
        assertThat(mapping.getValue().getMergeMethod()).isEqualTo(MergeMethod.REGIONAL_TYPE);
        verify(disasterEventRepository).incrementOnMerge(ADJACENT_EVENT_ID, now);
        verify(disasterEventRepository, never()).save(any(DisasterEvent.class));
    }

    @Test
    @DisplayName("b) 같은 시군구 대상 있음: 인접 조회 없이 같은 시군구 이벤트로 병합 [회귀 보호]")
    void sameSigunguTarget_skipsAdjacentLookup() {
        alert("산불", FIRE_INCIDENT, SIGUNGU_CODE);
        when(disasterEventRepository.findRegionalTypeMergeTarget(
                anyString(), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(Optional.of(SAME_SIGUNGU_EVENT_ID));
        stubAdjacentTarget();

        service.clusterNewAlert(1L);

        verifyAdjacentNeverQueried();
        verify(disasterEventRepository).incrementOnMerge(SAME_SIGUNGU_EVENT_ID, now);
        ArgumentCaptor<EventAlertMapping> mapping = ArgumentCaptor.forClass(EventAlertMapping.class);
        verify(eventAlertMappingRepository).save(mapping.capture());
        assertThat(mapping.getValue().getMergeMethod()).isEqualTo(MergeMethod.REGIONAL_TYPE);
    }

    @Test
    @DisplayName("c) 같은 시군구·인접 모두 없음: 신규 이벤트(비안내·비광역) 생성 [회귀 보호]")
    void noTargets_createsNewEvent() {
        alert("산불", FIRE_INCIDENT, SIGUNGU_CODE);

        service.clusterNewAlert(1L);

        ArgumentCaptor<DisasterEvent> event = ArgumentCaptor.forClass(DisasterEvent.class);
        verify(disasterEventRepository).save(event.capture());
        assertThat(event.getValue().isAdvisory()).isFalse();
        assertThat(event.getValue().isBroadcast()).isFalse();
    }

    @Test
    @DisplayName("e) 산사태·홍수 사건(설정에 없는 유형): 인접 조회 없음 [회귀 보호]")
    void landslideAndFlood_neverQueryAdjacent() {
        alert("산사태", "산사태 발생. 인근 주민은 대피바랍니다.", SIGUNGU_CODE);
        stubAdjacentTarget();
        service.clusterNewAlert(1L);

        alert("홍수", "하천 범람 위험. 인근 주민은 대피바랍니다.", SIGUNGU_CODE);
        service.clusterNewAlert(1L);

        verifyAdjacentNeverQueried();
    }

    @Test
    @DisplayName("f) regionalAdjacentTypesCsv 가 빈 문자열: 산불이어도 인접 조회 없음")
    void emptyAdjacentTypes_neverQueriesAdjacent() {
        ReflectionTestUtils.setField(service, "regionalAdjacentTypesCsv", "");
        alert("산불", FIRE_INCIDENT, SIGUNGU_CODE);
        stubAdjacentTarget();

        service.clusterNewAlert(1L);

        verifyAdjacentNeverQueried();
    }

    @Test
    @DisplayName("g) regionalAdjacentWindowHours=12: since 가 createdAt-12h")
    void adjacentWindowHours_isConfigurable() {
        ReflectionTestUtils.setField(service, "regionalAdjacentWindowHours", 12);
        alert("산불", FIRE_INCIDENT, SIGUNGU_CODE);

        service.clusterNewAlert(1L);

        verify(disasterEventRepository).findRegionalTypeAdjacentMergeTarget(
                eq("산불"), any(String[].class), eq(now.minusHours(12)));
    }

    @Test
    @DisplayName("h) 시군구 2개 산불 사건: 두 시군구 prefix 배열이 인접 조회에 그대로 전달")
    void multiSigungu_passesAllPrefixes() {
        alert("산불", FIRE_INCIDENT, "4781000000", "4783000000");

        service.clusterNewAlert(1L);

        verify(disasterEventRepository).findRegionalTypeAdjacentMergeTarget(
                eq("산불"), eq(new String[]{"47810", "47830"}), eq(now.minusHours(24)));
    }

    @Test
    @DisplayName("i) 시군구 11개(maxRegionSpan 초과) 산불 사건: 인접 조회 없음 [회귀 보호]")
    void overMaxSpan_neverQueriesAdjacent() {
        alert("산불", FIRE_INCIDENT, sigunguCodes(11));
        stubAdjacentTarget();

        service.clusterNewAlert(1L);

        verifyAdjacentNeverQueried();
    }
}
