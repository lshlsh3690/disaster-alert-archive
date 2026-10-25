package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.AlertKind;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import com.disaster.alert.alertapi.domain.event.repository.AlertEmbeddingRepository;
import com.disaster.alert.alertapi.domain.event.repository.ClusteringTraceRepository;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 비사건(ADVISORY) 알림은 이벤트 클러스터링에서 제외하고, 사건(INCIDENT)·미분류(null)는 지금처럼 클러스터링한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventClusteringServiceAlertKindTest {

    @Mock EmbeddingModel embeddingModel;
    @Mock DisasterAlertRepository disasterAlertRepository;
    @Mock DisasterEventRepository disasterEventRepository;
    @Mock EventAlertMappingRepository eventAlertMappingRepository;
    @Mock AlertEmbeddingRepository alertEmbeddingRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock EventLLMDecisionService llmDecisionService;
    @Mock ClusteringTraceRepository clusteringTraceRepository;

    EventClusteringService service;

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
        ReflectionTestUtils.setField(service, "accidentTypesCsv", "기타,화재,산불,붕괴,교통사고");
        ReflectionTestUtils.setField(service, "animalKeywords", "탈출|출몰");
        ReflectionTestUtils.setField(service, "globalTypesCsv", "태풍");
        ReflectionTestUtils.setField(service, "globalWindowHours", 168);
        ReflectionTestUtils.setField(service, "regionalTypesCsv", "산불:336,산사태:168,홍수:168");
    }

    /** 지역 코드가 없는 평범한 호우 알림 - 기존 경로에서는 임베딩 후 곧바로 신규 이벤트가 만들어진다. */
    private DisasterAlert rainAlert(AlertKind kind) {
        DisasterAlert alert = DisasterAlert.builder().sn(100L).disasterType("호우")
                .message("호우주의보 발효. 저지대 침수에 유의하세요.").createdAt(LocalDateTime.now()).build();
        ReflectionTestUtils.setField(alert, "id", 1L);
        if (kind != null) {
            alert.classifyAs(kind);
        }
        when(disasterAlertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});
        when(disasterEventRepository.save(any(DisasterEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        return alert;
    }

    // 결정: LLM 이 비사건(ADVISORY)으로 분류한 알림은 이벤트·위험도 파이프라인에 들어가지 않는다.
    // Red: 현재는 alertKind 를 안 보고 임베딩 -> 신규 이벤트 저장 -> 이벤트 발행까지 해서 never 검증이 실패한다.
    @Test
    @DisplayName("ADVISORY 알림은 임베딩·이벤트 저장·매핑·발행을 하지 않는다")
    void advisoryAlertIsNotClustered() {
        rainAlert(AlertKind.ADVISORY);

        service.clusterNewAlert(1L);

        verifyNoInteractions(embeddingModel, disasterEventRepository, eventAlertMappingRepository, eventPublisher);
    }

    // 결정: 사건(INCIDENT)과 분류 실패·기존 알림(null)은 지금처럼 신규 이벤트를 만든다(폴백).
    // Red: 게이트가 없는 현재 동작이라 이미 통과한다 - 게이트가 INCIDENT/null 까지 막는 회귀를 지키는 가드다.
    @Test
    @DisplayName("INCIDENT·null(미분류) 알림은 지금처럼 신규 이벤트를 만든다")
    void incidentAndUnclassifiedAlertsAreStillClustered() {
        for (AlertKind kind : new AlertKind[]{AlertKind.INCIDENT, null}) {
            rainAlert(kind);

            service.clusterNewAlert(1L);
        }

        verify(disasterEventRepository, org.mockito.Mockito.times(2)).save(any(DisasterEvent.class));
    }
}
