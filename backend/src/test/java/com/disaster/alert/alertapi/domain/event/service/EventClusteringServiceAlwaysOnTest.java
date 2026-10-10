package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import com.disaster.alert.alertapi.domain.event.repository.AlertEmbeddingRepository;
import com.disaster.alert.alertapi.domain.event.repository.DisasterEventRepository;
import com.disaster.alert.alertapi.domain.event.repository.EventAlertMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 클러스터링·LLM 폴백이 설정(플래그) 없이 항상 동작하는지 검증한다.
 * enabled / llmFallbackEnabled 필드는 일부러 건드리지 않는다 — 기본값(false)인 상태에서도 동작해야 한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventClusteringServiceAlwaysOnTest {

    @Mock EmbeddingModel embeddingModel;
    @Mock DisasterAlertRepository disasterAlertRepository;
    @Mock DisasterEventRepository disasterEventRepository;
    @Mock EventAlertMappingRepository eventAlertMappingRepository;
    @Mock AlertEmbeddingRepository alertEmbeddingRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock EventLLMDecisionService llmDecisionService;

    EventClusteringService service;

    @BeforeEach
    void setUp() {
        service = new EventClusteringService(embeddingModel, disasterAlertRepository, disasterEventRepository,
                eventAlertMappingRepository, alertEmbeddingRepository, eventPublisher, llmDecisionService);
        // application.yml 기본값 (플래그 필드는 설정하지 않는다)
        ReflectionTestUtils.setField(service, "similarityThreshold", 0.85);
        ReflectionTestUtils.setField(service, "candidateWindowHours", 168);
        ReflectionTestUtils.setField(service, "maxRegionSpan", 10);
        ReflectionTestUtils.setField(service, "nationwideSidoSpan", 8);
        ReflectionTestUtils.setField(service, "personWindowHours", 336);
        ReflectionTestUtils.setField(service, "llmFallbackDistanceCeil", 0.40);
        ReflectionTestUtils.setField(service, "accidentTypesCsv",
                "기타,화재,산불,붕괴,교통사고,교통통제,교통,환경오염사고,정전,통신,테러,지진,지진해일,수도");
        ReflectionTestUtils.setField(service, "animalKeywords", "탈출|출몰|멧돼지|들개|늑대");
        ReflectionTestUtils.setField(service, "globalTypesCsv", "태풍");
        ReflectionTestUtils.setField(service, "globalWindowHours", 168);
        ReflectionTestUtils.setField(service, "regionalTypesCsv", "산불:336,산사태:168,홍수:168");
        ReflectionTestUtils.setField(service, "advisorySplitTypesCsv", "산불");
    }

    @Test
    @DisplayName("설정 없이도 clusterNewAlert 는 알림을 조회한다")
    void clusterNewAlert_queriesAlertWithoutAnyFlag() {
        when(disasterAlertRepository.findById(1L)).thenReturn(Optional.empty());

        service.clusterNewAlert(1L);

        verify(disasterAlertRepository).findById(1L);
    }

    @Test
    @DisplayName("설정 없이도 사고성 유형의 borderline 후보가 있으면 LLM 판정을 호출한다")
    void clusterNewAlert_callsLlmFallbackWithoutAnyFlag() {
        LocalDateTime now = LocalDateTime.now();
        DisasterAlert alert = DisasterAlert.builder()
                .sn(100L)
                .message("서소문 고가 인근 도로 통제 중입니다. 우회 바랍니다.")
                .createdAt(now)
                .disasterType("교통통제")
                .build();
        ReflectionTestUtils.setField(alert, "id", 1L);
        alert.addRegionCode("1114000000");

        long eventId = 77L;
        DisasterEvent event = DisasterEvent.createFromFirstAlert(
                "교통통제", "1114000000", "서울특별시 중구", "서소문 고가 열차 중지", now.minusHours(1), false);

        when(disasterAlertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(alertEmbeddingRepository.findEmbeddingText(1L)).thenReturn("[0.1,0.2]");
        when(disasterEventRepository.findTopCandidates(anyString(), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(List.<Object[]>of(new Object[]{eventId, 0.30}));
        when(disasterEventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(llmDecisionService.pickSameGeneralIncident(anyString(), anyList())).thenReturn(null);
        when(disasterEventRepository.save(any(DisasterEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        service.clusterNewAlert(1L);

        verify(llmDecisionService).pickSameGeneralIncident(anyString(), anyList());
    }
}
