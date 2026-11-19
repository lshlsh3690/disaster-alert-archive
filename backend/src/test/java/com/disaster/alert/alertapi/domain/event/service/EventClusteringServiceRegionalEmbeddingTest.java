package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import com.disaster.alert.alertapi.domain.event.model.MergeMethod;
import com.disaster.alert.alertapi.domain.event.repository.AlertEmbeddingRepository;
import com.disaster.alert.alertapi.domain.event.repository.DisasterEventRepository;
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
 * 지역 한정 유형(산불·산사태·홍수·호우)도 임베딩을 "저장만" 해 두는지 검증.
 *
 * <p>지역 한정 유형은 유형+시군구+시간창 규칙으로 병합하고 바로 끝나서 임베딩을 한 번도 만들지 않았다.
 * 그 결과 00:30 구조 검사가 이 알림들을 "임베딩 없음"(장애 신호)으로 세고, 이 이벤트들이
 * 다른 유형 문자의 임베딩 후보(embedding IS NOT NULL)로도 잡히지 않았다.
 * 묶는 판단은 지금처럼 규칙이 하고, 임베딩은 판단에 쓰지 않는다. 순수 Mockito 단위 테스트(DB 없음).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventClusteringServiceRegionalEmbeddingTest {

    private static final long ALERT_ID = 1L;
    private static final long TARGET_EVENT_ID = 55L;
    private static final String SIGUNGU_CODE = "4781000000";
    private static final String FIRE_INCIDENT =
            "오늘 18:24 군북면 수곡리 320-4 산불 확산. 인근 주민은 대피바랍니다.";

    @Mock EmbeddingModel embeddingModel;
    @Mock DisasterAlertRepository disasterAlertRepository;
    @Mock DisasterEventRepository disasterEventRepository;
    @Mock AlertEmbeddingRepository alertEmbeddingRepository;
    @Mock EventLLMDecisionService llmDecisionService;

    EventClusteringService service;
    LocalDateTime now = LocalDateTime.of(2026, 7, 20, 12, 0);
    DisasterEvent targetEvent;

    @BeforeEach
    void setUp() {
        service = new EventClusteringService(embeddingModel, disasterAlertRepository, disasterEventRepository,
                alertEmbeddingRepository, llmDecisionService);
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
        ReflectionTestUtils.setField(service, "sustainedTypesCsv", "폭염,한파,대설,강풍,가축질병");
        ReflectionTestUtils.setField(service, "sustainedWindowHours", 168);
        ReflectionTestUtils.setField(service, "regionalAdjacentTypesCsv", "산불");
        ReflectionTestUtils.setField(service, "regionalAdjacentWindowHours", 24);

        // 이 알림에는 저장된 임베딩이 아직 없다 - "임베딩 없음" 상태가 이 테스트의 출발점이다.
        when(alertEmbeddingRepository.findEmbeddingText(ALERT_ID)).thenReturn(null);
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});

        // 규칙이 같은 시군구의 진행 중 산불 이벤트(55번)를 병합 대상으로 고른다.
        targetEvent = DisasterEvent.builder().id(TARGET_EVENT_ID).primaryDisasterType("산불")
                .firstAlertAt(now.minusHours(10)).lastAlertAt(now.minusHours(2)).alertCount(3).build();
        when(disasterEventRepository.findById(TARGET_EVENT_ID)).thenReturn(Optional.of(targetEvent));
        when(disasterEventRepository.findRegionalTypeMergeTarget(
                anyString(), any(String[].class), any(LocalDateTime.class))).thenReturn(Optional.of(TARGET_EVENT_ID));
        when(disasterEventRepository.findTopCandidates(anyString(), any(String[].class), any(LocalDateTime.class), any(String[].class)))
                .thenReturn(List.of());

        DisasterAlert alert = DisasterAlert.builder()
                .sn(100L).message(FIRE_INCIDENT).createdAt(now).disasterType("산불")
                .emergencyLevel(DisasterLevel.LEVEL_1).build();
        ReflectionTestUtils.setField(alert, "id", ALERT_ID);
        alert.addRegionCode(SIGUNGU_CODE);
        when(disasterAlertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert));
    }

    /** 알림이 규칙이 고른 55번 이벤트에 REGIONAL_TYPE 으로 붙었는지 확인한다. */
    private void assertMergedByRuleIntoTarget() {
        ArgumentCaptor<DisasterAlert> saved = ArgumentCaptor.forClass(DisasterAlert.class);
        verify(disasterAlertRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEventMergeMethod()).isEqualTo(MergeMethod.REGIONAL_TYPE);
        assertThat(saved.getValue().getEvent()).as("알림이 규칙이 고른 이벤트에 연결돼야 한다").isNotNull()
                .extracting(DisasterEvent::getId).isEqualTo(TARGET_EVENT_ID);
        // 신규 이벤트가 생기지 않았다 = 임베딩 때문에 병합 대상이 바뀌지 않았다.
        verify(disasterEventRepository, never()).save(any(DisasterEvent.class));
    }

    /**
     * 같은 시군구 규칙 병합(REGIONAL_TYPE)에서도 임베딩이 저장되고, 병합 대상은 규칙이 고른 그대로다.
     * Red: 지금은 clusterRegionalType 이 임베딩 확보 전에 끝내므로 updateEmbedding 이 한 번도 불리지 않는다.
     */
    @Test
    @DisplayName("지역 한정 유형이 규칙으로 병합돼도 임베딩이 저장되고, 병합 대상은 규칙이 고른 이벤트 그대로다")
    void regionalMerge_storesEmbeddingWithoutChangingTarget() {
        // When: 산불 알림이 같은 시군구 기존 이벤트로 규칙 병합되는 경로를 탄다.
        service.clusterNewAlert(ALERT_ID);

        // Then 1: 임베딩이 이 알림의 것으로 저장됐다(저장 안 하면 구조 검사가 계속 "임베딩 없음"으로 센다).
        verify(alertEmbeddingRepository).updateEmbedding(eq(ALERT_ID), anyString());
        // Then 2: 병합은 규칙 결과 그대로 - 임베딩 후보 검색(findTopCandidates)으로 판단을 바꾸지 않는다.
        assertMergedByRuleIntoTarget();
        verify(disasterEventRepository, never()).findTopCandidates(
                anyString(), any(String[].class), any(LocalDateTime.class), any(String[].class));
    }

    /**
     * 임베딩 생성이 실패해도 규칙 병합은 그대로 이뤄진다 - 저장만 하는 부가 작업이 클러스터링을 막으면
     * 알림이 미배정으로 남는다. embed 를 실제로 불렀는지도 확인해야 "아예 안 불러서 통과"하는 빈 테스트가 되지 않는다.
     * Red: 지금은 embed 를 아예 안 불러 embed 호출 단언에서 실패한다.
     */
    @Test
    @DisplayName("임베딩 생성이 예외로 실패해도 규칙 병합은 그대로 이뤄진다")
    void embeddingFailure_doesNotBlockRuleMerge() {
        // Given: OpenAI 임베딩 호출이 실패한다(네트워크 오류·한도 초과 등).
        when(embeddingModel.embed(anyString())).thenThrow(new RuntimeException("embedding down"));

        // When: 예외가 clusterNewAlert 밖으로 새어 나오면 안 된다.
        service.clusterNewAlert(ALERT_ID);

        // Then: 임베딩 시도는 했고(빈 테스트 방지), 실패했으니 저장은 없으며, 병합은 규칙대로 끝났다.
        verify(embeddingModel).embed(anyString());
        verify(alertEmbeddingRepository, never()).updateEmbedding(eq(ALERT_ID), anyString());
        assertMergedByRuleIntoTarget();
    }
}
