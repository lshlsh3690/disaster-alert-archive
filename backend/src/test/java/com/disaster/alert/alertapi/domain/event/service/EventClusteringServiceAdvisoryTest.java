package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
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
 * 안내성 분리(산불) 라우팅 검증.
 *
 * <p>폭염·한파의 안내성 분리는 에피소드형 유형 앵커링({@link EventClusteringServiceEpisodicTypeTest})으로
 * 대체되어 이 클래스에서 다루지 않는다. 안내성 분리는 {@code advisory-split-types} 기본값 "산불" 만 대상이며,
 * 레포 메서드 {@code findAdvisoryMergeTarget(type, sigunguCodes, since)} 의 윈도우는 산불의 regional-types
 * 윈도우(336h)다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventClusteringServiceAdvisoryTest {

    private static final String HEAT_GUIDE =
            "오늘 무더위가 예상됩니다.▲낮 시간 논·밭작업 및 야외활동 자제▲무더위쉼터 이용"
                    + "▲폭염안전수칙(물,그늘,휴식)을 준수하여 건강관리에 유의하시기 바랍니다.[홍성군]";

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
        // 폭염·한파는 더 이상 안내성 분리를 하지 않는다(에피소드형 앵커링으로 대체) — 새 기본값 "산불".
        ReflectionTestUtils.setField(service, "advisorySplitTypesCsv", "산불");

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
    @DisplayName("다지역 폭염 안내(시군구 2개/3개)는 안내 경로가 아니라 기존 임베딩 후보 검색 경로다 - 안내 이벤트 blob 방지")
    void multiRegionHeatAdvisory_goesEmbeddingPath() {
        String[][] cases = {
                {"4480000000", "4375000000"},
                {"4480000000", "4375000000", "4376000000"}};
        for (String[] codes : cases) {
            org.mockito.Mockito.clearInvocations(disasterEventRepository, embeddingModel, eventPublisher);
            alert("폭염", HEAT_GUIDE, DisasterLevel.LEVEL_1, codes);
            when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});

            service.clusterNewAlert(1L);

            verify(disasterEventRepository, never())
                    .findAdvisoryMergeTarget(anyString(), any(String[].class), any(LocalDateTime.class));
            verify(disasterEventRepository)
                    .findTopCandidates(anyString(), any(String[].class), any(LocalDateTime.class));
            ArgumentCaptor<DisasterEvent> event = ArgumentCaptor.forClass(DisasterEvent.class);
            verify(disasterEventRepository).save(event.capture());
            assertThat(event.getValue().isAdvisory()).as(String.valueOf(codes.length)).isFalse();
        }
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
