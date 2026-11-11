package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.AlertKind;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import com.disaster.alert.alertapi.domain.event.model.MergeMethod;
import com.disaster.alert.alertapi.domain.event.repository.AlertEmbeddingRepository;
import com.disaster.alert.alertapi.domain.event.repository.DisasterEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
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
    @Mock AlertEmbeddingRepository alertEmbeddingRepository;
    @Mock EventLLMDecisionService llmDecisionService;

    EventClusteringService service;

    @BeforeEach
    void setUp() {
        service = new EventClusteringService(embeddingModel, disasterAlertRepository, disasterEventRepository,
                alertEmbeddingRepository, llmDecisionService);
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

    // 결정: LLM 이 비사건(ADVISORY)으로 분류한 알림은 이벤트 파이프라인에 들어가지 않는다.
    // Red: 현재는 alertKind 를 안 보고 임베딩 -> 신규 이벤트 저장 -> 이벤트 발행까지 해서 never 검증이 실패한다.
    @Test
    @DisplayName("ADVISORY 알림은 임베딩·이벤트 저장·매핑을 하지 않는다")
    void advisoryAlertIsNotClustered() {
        rainAlert(AlertKind.ADVISORY);

        service.clusterNewAlert(1L);

        verifyNoInteractions(embeddingModel, disasterEventRepository);
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

    // 결정: 이미 이벤트에 들어 있는 알림은 clusterNewAlert 가 다시 와도 조용히 건너뛴다.
    // 이유: 예전엔 event_alert_mapping 의 alert_id UNIQUE 제약이 같은 알림의 재클러스터링을 막았지만,
    //       이제 소속은 disaster_alert.event_id 한 칸이라 가드가 없으면 다른 이벤트 id 로 조용히 덮어써진다.
    // Red: 가드가 아직 없어 임베딩 호출 -> 신규 이벤트 저장 -> 알림이 새 이벤트로 재배정되어 아래 단언들이 실패한다.
    @Test
    @DisplayName("이미 이벤트에 배정된 알림은 임베딩·이벤트 저장 없이 건너뛰고 기존 배정을 유지한다")
    void alreadyAssignedAlertIsSkipped() {
        // 준비: 호우 알림을 이벤트 7 에 이미 배정해 둔다(수집 재시도·백필 재실행으로 같은 알림이 다시 오는 상황).
        DisasterAlert alert = rainAlert(null);
        DisasterEvent existing = DisasterEvent.builder().build();
        ReflectionTestUtils.setField(existing, "id", 7L);
        alert.assignToEvent(existing, 1, null, MergeMethod.SEED);

        // 실행: 같은 알림을 한 번 더 클러스터링하려 한다.
        service.clusterNewAlert(1L);

        // 검증 1: 임베딩(유료 호출)과 이벤트 리포지토리(후보 검색·저장)를 전혀 건드리지 않는다.
        verifyNoInteractions(embeddingModel, disasterEventRepository);
        // 검증 2: 알림 저장도 없다 — 재배정이 일어났다면 save 나 더티 체킹으로 새 소속이 반영됐을 것이다.
        verify(disasterAlertRepository, never()).save(any(DisasterAlert.class));
        // 검증 3: 소속은 여전히 이벤트 7 이다(덮어쓰기 방지가 이 가드의 존재 이유).
        assertThat(alert.getEventId()).isEqualTo(7L);
    }
}
