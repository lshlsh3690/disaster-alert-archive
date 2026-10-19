package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.AlertKind;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import com.disaster.alert.alertapi.domain.event.model.EventAlertMapping;
import com.disaster.alert.alertapi.domain.event.repository.AlertEmbeddingRepository;
import com.disaster.alert.alertapi.domain.event.repository.DisasterEventRepository;
import com.disaster.alert.alertapi.domain.event.repository.EventAlertMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 클러스터링 판정 기록(clustering_trace) 검증 — clusterNewAlert 가 알림 1건을 처리하면서 이미 아는 값
 * (경로·후보·기준 거리·LLM 결과·최종 결과)을 recorder 에 한 번 넘기는지 본다. 순수 Mockito 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventClusteringServiceTraceTest {

    private static final String REGION = "4480000000";
    private static final long EVENT_A = 55L;
    private static final long EVENT_B = 56L;
    private static final String FIRE = "홍성군 OO동 공장 화재 발생, 인근 주민은 대피 바랍니다.";

    @Mock EmbeddingModel embeddingModel;
    @Mock DisasterAlertRepository disasterAlertRepository;
    @Mock DisasterEventRepository disasterEventRepository;
    @Mock EventAlertMappingRepository eventAlertMappingRepository;
    @Mock AlertEmbeddingRepository alertEmbeddingRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock EventLLMDecisionService llmDecisionService;
    @Mock ClusteringTraceRecorder traceRecorder;

    EventClusteringService service;
    LocalDateTime now = LocalDateTime.of(2026, 7, 20, 12, 0);

    @BeforeEach
    void setUp() {
        service = newService(traceRecorder);
        when(disasterEventRepository.save(any(DisasterEvent.class))).thenAnswer(inv -> {
            DisasterEvent e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", 900L);
            return e;
        });
        when(alertEmbeddingRepository.findEmbeddingText(1L)).thenReturn("[0.1,0.2]");
        when(eventAlertMappingRepository.countByEventId(anyLong())).thenReturn(2);
    }

    private EventClusteringService newService(ClusteringTraceRecorder recorder) {
        EventClusteringService s = new EventClusteringService(embeddingModel, disasterAlertRepository,
                disasterEventRepository, eventAlertMappingRepository, alertEmbeddingRepository, eventPublisher,
                llmDecisionService, recorder);
        ReflectionTestUtils.setField(s, "similarityThreshold", 0.85); // 병합 기준 거리 0.15
        ReflectionTestUtils.setField(s, "candidateWindowHours", 168);
        ReflectionTestUtils.setField(s, "maxRegionSpan", 10);
        ReflectionTestUtils.setField(s, "nationwideSidoSpan", 8);
        ReflectionTestUtils.setField(s, "personWindowHours", 336);
        ReflectionTestUtils.setField(s, "llmFallbackDistanceCeil", 0.40);
        ReflectionTestUtils.setField(s, "accidentTypesCsv", "기타,화재,산불,붕괴");
        ReflectionTestUtils.setField(s, "animalKeywords", "탈출|출몰|멧돼지|들개|늑대");
        ReflectionTestUtils.setField(s, "globalTypesCsv", "태풍");
        ReflectionTestUtils.setField(s, "globalWindowHours", 168);
        ReflectionTestUtils.setField(s, "regionalTypesCsv", "산불:168,산사태:168,홍수:168");
        ReflectionTestUtils.setField(s, "regionalAdjacentTypesCsv", "");
        ReflectionTestUtils.setField(s, "regionalAdjacentWindowHours", 24);
        ReflectionTestUtils.setField(s, "relaxedThresholdTypesCsv", "폭염,한파,대설,강풍,가축질병");
        ReflectionTestUtils.setField(s, "relaxedSimilarityThreshold", 0.65);
        ReflectionTestUtils.setField(s, "episodicTypesCsv", "폭염,한파,대설,강풍,가축질병");
        ReflectionTestUtils.setField(s, "episodicWindowHours", 168);
        ReflectionTestUtils.setField(s, "adjacentWindowHours", 48);
        ReflectionTestUtils.setField(s, "adjacentMaxEventAgeHours", 72);
        return s;
    }

    private DisasterAlert alert(String type, String message, AlertKind kind) {
        DisasterAlert a = DisasterAlert.builder()
                .sn(100L).message(message).createdAt(now).disasterType(type)
                .emergencyLevel(DisasterLevel.LEVEL_1).build();
        ReflectionTestUtils.setField(a, "id", 1L);
        a.addRegionCode(REGION);
        if (kind != null) {
            a.classifyAs(kind);
        }
        when(disasterAlertRepository.findById(1L)).thenReturn(Optional.of(a));
        return a;
    }

    private void sameRegionCandidates(double... distances) {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < distances.length; i++) {
            rows.add(new Object[]{EVENT_A + i, distances[i]});
        }
        when(disasterEventRepository.findTopCandidates(anyString(), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(rows);
    }

    /** 후보 이벤트 로드 스텁 — LLM 폴백 가드(사고성 유형, 72h)를 통과하는 화재 이벤트. */
    private void fireEvent(long eventId) {
        DisasterEvent ev = DisasterEvent.builder().primaryDisasterType("화재")
                .firstAlertAt(now.minusHours(15)).lastAlertAt(now.minusHours(10)).build();
        ReflectionTestUtils.setField(ev, "id", eventId);
        when(disasterEventRepository.findById(eventId)).thenReturn(Optional.of(ev));
    }

    private ClusteringTrace recordedTrace() {
        ArgumentCaptor<ClusteringTrace> captor = ArgumentCaptor.forClass(ClusteringTrace.class);
        verify(traceRecorder).record(captor.capture());
        return captor.getValue();
    }

    // 결정: 임베딩 경로에서 "후보는 올랐지만 기준을 넘었고 LLM 도 없음이라 신규"가 된 이유를, 판정이 본 값 그대로 남긴다.
    //       (놓친 쌍을 진단할 때 후보에 있었는지·거리·기준·LLM 결과를 다시 계산하지 않고 읽기 위해)
    // Red: 뼈대 서비스는 recorder 를 호출하지 않아 verify(record) 가 "호출 없음"으로 실패한다.
    @Test
    @DisplayName("임베딩 경로: 후보 2건이 기준을 넘고 LLM 이 없음 → EMBEDDING/NEW, 후보·기준·LLM 이 기록된다")
    void embeddingPath_candidatesOverThresholdAndLlmNone_recordsNew() {
        // 화재(사고성) + 후보 0.21, 0.33: 둘 다 기준 0.15 초과, LLM 대상 구간(0.15~0.40) 안
        alert("화재", FIRE, null);
        sameRegionCandidates(0.21, 0.33);
        fireEvent(EVENT_A);
        fireEvent(EVENT_B);
        // LLM 이 "같은 사건 없음"(null)으로 답한다 — Mockito 기본값은 Integer 0 이라 반드시 명시
        when(llmDecisionService.pickSameGeneralIncident(anyString(), anyList())).thenReturn(null);

        service.clusterNewAlert(1L);

        ClusteringTrace t = recordedTrace();
        assertThat(t.alertId()).isEqualTo(1L);
        assertThat(t.path()).isEqualTo(ClusteringTrace.Path.EMBEDDING);
        // 거리는 소수 4자리, 거리 오름차순 그대로
        assertThat(t.candidates()).isEqualTo("55:0.2100, 56:0.3300");
        // 적용된 병합 기준 거리 = 1 - 0.85
        assertThat(t.threshold()).isCloseTo(0.15, within(1e-9));
        // LLM 에 넘긴 후보는 가드 통과한 2건이고 답은 없음
        assertThat(t.llm()).isEqualTo("후보 2건 → 없음");
        assertThat(t.result()).isEqualTo(ClusteringTrace.Result.NEW);
        assertThat(t.eventId()).isEqualTo(900L); // 새로 만든 이벤트 id
    }

    // 결정: 키로 정해지는 경로(지역 한정 유형)는 본문 거리를 쓰지 않으므로 후보·기준·LLM 칸이 비어야 한다.
    // Red: recorder 가 호출되지 않아 실패한다.
    @Test
    @DisplayName("지역 한정 유형 병합: REGIONAL_TYPE/MERGED, event_id 는 병합 대상, 후보·기준·LLM 은 null")
    void regionalTypeMerge_recordsKeyPathWithoutEmbeddingFields() {
        alert("산불", "산불 확산. 인근 주민은 대피바랍니다.", null);
        when(disasterEventRepository.findRegionalTypeMergeTarget(anyString(), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(Optional.of(EVENT_A));

        service.clusterNewAlert(1L);

        ClusteringTrace t = recordedTrace();
        assertThat(t.path()).isEqualTo(ClusteringTrace.Path.REGIONAL_TYPE);
        assertThat(t.result()).isEqualTo(ClusteringTrace.Result.MERGED);
        assertThat(t.eventId()).isEqualTo(EVENT_A);
        assertThat(t.candidates()).isNull();
        assertThat(t.threshold()).isNull();
        assertThat(t.llm()).isNull();
    }

    // 결정: 비사건(ADVISORY)은 이벤트를 만들지 않지만 "건너뛰었다"는 사실은 알림당 한 행으로 남긴다.
    // Red: recorder 가 호출되지 않아 실패한다(이벤트 미생성 검증은 지금도 통과).
    @Test
    @DisplayName("ADVISORY 알림: ADVISORY_SKIPPED/SKIPPED, event_id null, 이벤트 생성·병합 없음")
    void advisoryAlert_recordsSkippedWithoutClustering() {
        alert("호우", "호우주의보 발효 중, 하천 접근을 자제 바랍니다.", AlertKind.ADVISORY);

        service.clusterNewAlert(1L);

        ClusteringTrace t = recordedTrace();
        assertThat(t.path()).isEqualTo(ClusteringTrace.Path.ADVISORY_SKIPPED);
        assertThat(t.result()).isEqualTo(ClusteringTrace.Result.SKIPPED);
        assertThat(t.eventId()).isNull();
        verify(disasterEventRepository, never()).save(any(DisasterEvent.class));
        verify(eventAlertMappingRepository, never()).save(any(EventAlertMapping.class));
    }

    // 결정: 기록은 관찰만 한다 — 같은 시나리오를 기록 없는 recorder 와 기록하는 recorder 로 돌려도 병합 결과가 같아야 한다.
    // Red: 병합 결과 비교는 지금도 같지만, 기록하는 recorder 가 한 건도 받지 못해 "기록 1건" 단언에서 실패한다.
    @Test
    @DisplayName("기록 유무와 무관하게 병합 결과가 같고, 기록하는 쪽에는 1건이 들어온다")
    void outcomeIsIdenticalWithAndWithoutRecording() {
        alert("화재", FIRE, null);
        sameRegionCandidates(0.10); // 기준 0.15 이내 → 후보 55 에 EMBEDDING 병합

        // (a) 아무것도 기록하지 않는 recorder
        newService(trace -> { }).clusterNewAlert(1L);
        String withoutRecording = outcome();
        Mockito.clearInvocations(eventAlertMappingRepository, disasterEventRepository, eventPublisher);

        // (b) 기록을 받아 모으는 recorder
        List<ClusteringTrace> recorded = new ArrayList<>();
        newService(recorded::add).clusterNewAlert(1L);
        String withRecording = outcome();

        assertThat(withoutRecording).isEqualTo("EMBEDDING->55");
        assertThat(withRecording).isEqualTo(withoutRecording);
        assertThat(recorded).hasSize(1);
    }

    /** 이번 실행의 결과를 "병합방식->대상 이벤트" 한 줄로 요약한다(신규면 "SEED->900"). */
    private String outcome() {
        ArgumentCaptor<EventAlertMapping> mapping = ArgumentCaptor.forClass(EventAlertMapping.class);
        verify(eventAlertMappingRepository).save(mapping.capture());
        EventAlertMapping m = mapping.getValue();
        return m.getMergeMethod() + "->" + m.getId().getEventId();
    }
}
