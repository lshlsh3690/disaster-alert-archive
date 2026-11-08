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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 화재 알림의 LLM 폴백 후보에 "이벤트 마지막 알림 72h 이내" 제한이 없어야 한다는 결정 검증.
 *
 * <p>후보 검색 쿼리가 이미 last_alert_at 을 candidate-time-window-hours(168h) 로 거르므로, 그 안쪽
 * 후보를 화재만 72h 로 한 번 더 자르면 같은 화재의 후속 "완진" 안내(3~7일 뒤)가 LLM 판정 기회조차 못 받는다.
 * 순수 Mockito 단위 테스트(DB·Spring 컨텍스트 없음).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventClusteringServiceFireLlmFallbackTest {

    private static final String REGION = "4831000000"; // 거제시
    private static final long EVENT_ID = 47189L;

    @Mock EmbeddingModel embeddingModel;
    @Mock DisasterAlertRepository disasterAlertRepository;
    @Mock DisasterEventRepository disasterEventRepository;
    @Mock EventAlertMappingRepository eventAlertMappingRepository;
    @Mock AlertEmbeddingRepository alertEmbeddingRepository;
    @Mock EventLLMDecisionService llmDecisionService;

    EventClusteringService service;

    @BeforeEach
    void setUp() {
        service = new EventClusteringService(embeddingModel, disasterAlertRepository, disasterEventRepository,
                eventAlertMappingRepository, alertEmbeddingRepository, llmDecisionService);
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
        ReflectionTestUtils.setField(service, "relaxedThresholdTypesCsv", "폭염,한파,대설,강풍,가축질병");
        ReflectionTestUtils.setField(service, "relaxedSimilarityThreshold", 0.65);
        ReflectionTestUtils.setField(service, "episodicTypesCsv", "");
        ReflectionTestUtils.setField(service, "adjacentWindowHours", 48);
        ReflectionTestUtils.setField(service, "adjacentMaxEventAgeHours", 72);

        when(disasterEventRepository.save(any(DisasterEvent.class))).thenAnswer(inv -> {
            DisasterEvent e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", 900L);
            return e;
        });
        when(alertEmbeddingRepository.findEmbeddingText(1L)).thenReturn("[0.1,0.2]");
        when(eventAlertMappingRepository.countByEventId(anyLong())).thenReturn(2);
    }

    /**
     * 거제 폐차장 화재 사례(평가 사본 알림 47189 → 47479) — 같은 시군구 후보 1개가 거리 0.3796 으로 폴백 구간
     * (임계 0.15 초과, 상한 0.40 이내)에 있고, 그 이벤트의 마지막 알림이 79.7h 전(72h 초과, 후보 검색 윈도우 168h 이내)이다.
     *
     * <p>예전에는 화재 한정 72h 가드가 이 후보를 비워 LLM 이 한 번도 호출되지 않고 단발 이벤트가 새로 만들어졌다.
     * 이 테스트는 그 가드를 제거한 뒤의 동작(후보가 살아남아 LLM 폴백을 타고 병합됨)을 고정한다.
     */
    @Test
    @DisplayName("화재 + 같은 시군구 후보의 마지막 알림이 79.7h 전(0.3796): LLM 폴백이 호출되어 같은 사건이면 병합")
    void fireCandidateOlderThan72h_stillGoesToLlmFallbackAndMerges() {
        // 준비: 01-12 화재 이벤트(알림 1건뿐이라 첫·마지막 알림 시각이 같다)와 79.7h 뒤 01-15 16:00 "완진" 안내
        LocalDateTime eventAt = LocalDateTime.of(2026, 1, 12, 8, 17, 7);
        LocalDateTime alertAt = LocalDateTime.of(2026, 1, 15, 16, 0, 21);
        DisasterAlert alert = DisasterAlert.builder()
                .sn(100L).message("지난 1/12 발생한 거제폐차장 화재는 완진, 상황 종료되었습니다.")
                .createdAt(alertAt).disasterType("화재").emergencyLevel(DisasterLevel.LEVEL_1).build();
        ReflectionTestUtils.setField(alert, "id", 1L);
        alert.addRegionCode(REGION);
        when(disasterAlertRepository.findById(1L)).thenReturn(Optional.of(alert));

        DisasterEvent event = DisasterEvent.builder()
                .primaryDisasterType("화재").firstAlertAt(eventAt).lastAlertAt(eventAt).build();
        ReflectionTestUtils.setField(event, "id", EVENT_ID);
        when(disasterEventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        // 거리 0.3796: 임베딩 병합(<=0.15)은 못 하고 LLM 폴백 구간(0.15 초과 ~ 0.40 이내)에 든다
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{EVENT_ID, 0.3796});
        when(disasterEventRepository.findTopCandidates(anyString(), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(rows);
        // LLM 이 첫 후보(index 0)를 같은 사건으로 고른다
        when(llmDecisionService.pickSameGeneralIncident(anyString(), anyList())).thenReturn(0);

        // 실행: 임베딩 → 같은 시군구 임계 병합 실패 → 인접 후보 없음 → LLM 폴백 순으로 지난다
        service.clusterNewAlert(1L);

        // 검증: 후보가 비워지지 않으므로 LLM 이 실제로 호출되어야 한다(옛 72h 가드가 있었다면 여기서 실패한다)
        verify(llmDecisionService).pickSameGeneralIncident(anyString(), anyList());
        // 검증: LLM 이 고른 이벤트에 LLM_FALLBACK 으로 붙고, 단발 신규 이벤트(save)는 만들어지지 않는다
        ArgumentCaptor<EventAlertMapping> mapping = ArgumentCaptor.forClass(EventAlertMapping.class);
        verify(eventAlertMappingRepository).save(mapping.capture());
        assertThat(mapping.getValue().getMergeMethod()).isEqualTo(MergeMethod.LLM_FALLBACK);
        verify(disasterEventRepository).incrementOnMerge(EVENT_ID, alertAt);
        verify(disasterEventRepository, never()).save(any(DisasterEvent.class));
    }
}
