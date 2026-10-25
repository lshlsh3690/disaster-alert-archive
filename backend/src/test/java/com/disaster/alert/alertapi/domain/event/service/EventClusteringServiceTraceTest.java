package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.AlertKind;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.event.model.ClusteringTrace;
import com.disaster.alert.alertapi.domain.event.model.ClusteringTracePath;
import com.disaster.alert.alertapi.domain.event.model.ClusteringTraceResult;
import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 클러스터링이 알림 1건마다 판정 기록(ClusteringTrace)을 남기는지 검증한다.
 * 설계: docs/event-clustering/ADMIN_EVAL_DASHBOARD_DESIGN.md 4절 ①. 순수 Mockito 단위 테스트(DB·Spring 없음).
 *
 * <p>Red 단계에서는 서비스가 ClusteringTraceRepository.upsert 를 아직 부르지 않으므로
 * 모든 테스트가 "Wanted but not invoked: clusteringTraceRepository.upsert(...)" 로 실패한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventClusteringServiceTraceTest {

    private static final String REGION = "4480000000";
    private static final long ALERT_ID = 1L;
    private static final String FIRE = "홍성군 OO동 공장 화재 발생, 인근 주민은 대피 바랍니다.";
    private static final String WILDFIRE = "홍성군 OO산 산불 발생, 입산을 금지합니다.";

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
        // 운영 기본값(application.yml)과 같게 맞춘다 - 경로 분기가 이 값들에 의존한다.
        ReflectionTestUtils.setField(service, "similarityThreshold", 0.85);
        ReflectionTestUtils.setField(service, "candidateWindowHours", 168);
        ReflectionTestUtils.setField(service, "maxRegionSpan", 10);
        ReflectionTestUtils.setField(service, "nationwideSidoSpan", 8);
        ReflectionTestUtils.setField(service, "personWindowHours", 336);
        ReflectionTestUtils.setField(service, "llmFallbackDistanceCeil", 0.40);
        ReflectionTestUtils.setField(service, "accidentTypesCsv", "기타,화재,산불,붕괴,교통사고");
        ReflectionTestUtils.setField(service, "animalKeywords", "탈출|출몰|멧돼지|들개|늑대");
        ReflectionTestUtils.setField(service, "globalTypesCsv", "태풍");
        ReflectionTestUtils.setField(service, "globalWindowHours", 168);
        ReflectionTestUtils.setField(service, "regionalTypesCsv", "산불:168,산사태:168,홍수:168");
        ReflectionTestUtils.setField(service, "regionalAdjacentTypesCsv", "산불");
        ReflectionTestUtils.setField(service, "regionalAdjacentWindowHours", 24);
        ReflectionTestUtils.setField(service, "relaxedThresholdTypesCsv", "폭염,한파,대설,강풍,가축질병");
        ReflectionTestUtils.setField(service, "relaxedSimilarityThreshold", 0.65);
        ReflectionTestUtils.setField(service, "episodicTypesCsv", "폭염,한파,대설,강풍,가축질병");
        ReflectionTestUtils.setField(service, "episodicWindowHours", 168);
        ReflectionTestUtils.setField(service, "adjacentWindowHours", 48);
        ReflectionTestUtils.setField(service, "adjacentMaxEventAgeHours", 72);

        when(disasterEventRepository.save(any(DisasterEvent.class))).thenAnswer(inv -> {
            DisasterEvent e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", 99L);   // 신규 이벤트 id - trace.eventId 가 이 값이어야 한다
            return e;
        });
        when(alertEmbeddingRepository.findEmbeddingText(ALERT_ID)).thenReturn("[0.1,0.2]");
        when(eventAlertMappingRepository.countByEventId(anyLong())).thenReturn(2);
    }

    private DisasterAlert alert(String type, String message) {
        DisasterAlert a = DisasterAlert.builder()
                .sn(100L).message(message).createdAt(now).disasterType(type)
                .emergencyLevel(DisasterLevel.LEVEL_1).build();
        ReflectionTestUtils.setField(a, "id", ALERT_ID);
        a.addRegionCode(REGION);
        when(disasterAlertRepository.findById(ALERT_ID)).thenReturn(Optional.of(a));
        return a;
    }

    private ClusteringTrace capturedTrace() {
        ArgumentCaptor<ClusteringTrace> captor = ArgumentCaptor.forClass(ClusteringTrace.class);
        verify(clusteringTraceRepository).upsert(captor.capture());
        return captor.getValue();
    }

    // 결정: 임베딩 경로에서 "후보는 봤지만 기준에 못 미치고 LLM 도 없음이라 해서 새 이벤트를 만든" 알림은
    //       후보·기준·LLM 호출 여부를 모두 남긴다. 인접 후보는 "조회했는데 0건"이라 null 이 아니라 빈 목록이어야
    //       대시보드가 "검색 안 함"과 "검색했는데 없음"을 구분한다.
    // Red: 서비스가 upsert 를 호출하지 않아 "Wanted but not invoked: clusteringTraceRepository.upsert" 로 실패한다.
    @Test
    @DisplayName("임베딩 경로 기준 미달 + LLM 없음: 후보·기준·LLM 호출을 담아 NEW 로 기록하고 인접 후보는 null 이 아닌 빈 목록")
    void embeddingPathNewEventAfterLlmNone_recordsCandidatesThresholdAndLlm() {
        // 준비: 사고성 유형(화재). 같은 시군구 후보 10번 이벤트가 거리 0.30 - 기준 0.15 초과, borderline 상한 0.40 이내라 LLM 대상.
        alert("화재", FIRE);
        when(disasterEventRepository.findTopCandidates(anyString(), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(List.<Object[]>of(new Object[]{10L, 0.30}));
        // 인접 시군구는 조회했지만 후보 없음 - 사고성 유형이라 같은 시군구 병합 실패 뒤 조회가 일어난다.
        when(disasterEventRepository.findTopAdjacentCandidates(anyString(), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        // LLM 폴백 가드 통과용 후보 이벤트: 사고성 유형 + 화재 72h 쿨다운 이내(마지막 알림 10시간 전).
        DisasterEvent candidate = DisasterEvent.builder().primaryDisasterType("화재")
                .firstAlertAt(now.minusHours(15)).lastAlertAt(now.minusHours(10)).build();
        ReflectionTestUtils.setField(candidate, "id", 10L);
        when(disasterEventRepository.findById(10L)).thenReturn(Optional.of(candidate));
        when(eventAlertMappingRepository.findRepresentativeMessages(anyList()))
                .thenReturn(List.<Object[]>of(new Object[]{10L, "공장 화재 발생"}));
        // LLM 이 "같은 사건 없음"(null) 을 고른다 -> 신규 이벤트.
        when(llmDecisionService.pickSameGeneralIncident(anyString(), anyList())).thenReturn(null);

        service.clusterNewAlert(ALERT_ID);

        ClusteringTrace trace = capturedTrace();
        assertThat(trace.getAlertId()).isEqualTo(ALERT_ID);
        assertThat(trace.getPath()).isEqualTo(ClusteringTracePath.EMBEDDING);
        assertThat(trace.getResult()).isEqualTo(ClusteringTraceResult.NEW);
        assertThat(trace.getEventId()).isEqualTo(99L);   // save 가 돌려준 신규 이벤트 id
        assertThat(trace.getCandidates()).containsExactly(new ClusteringTrace.Candidate(10L, 0.30));
        assertThat(trace.getAdjacentCandidates()).isNotNull().isEmpty();
        assertThat(trace.getThreshold()).isCloseTo(0.15, within(1e-9));   // 1 - similarityThreshold(0.85)
        assertThat(trace.isLlmCalled()).isTrue();
        assertThat(trace.getLlmResult()).isNull();
    }

    // 결정: 후보를 검색하지 않는 키 경로(지역 한정)에서는 후보·기준을 null 로 남기고 LLM 도 부르지 않았다고 적는다.
    //       빈 목록으로 채우면 "검색했는데 0건"과 구분이 안 돼 검색 단계 N개 중 M개 계산이 틀어진다.
    // Red: 서비스가 upsert 를 호출하지 않아 "Wanted but not invoked" 로 실패한다.
    @Test
    @DisplayName("지역 한정(산불) 키 병합: path=REGIONAL, MERGED, 후보·기준은 null, LLM 미호출")
    void regionalTypeMerge_recordsRegionalPathWithoutCandidates() {
        alert("산불", WILDFIRE);
        // 같은 시군구·같은 유형의 진행 중 이벤트 20번이 있다 -> 임베딩 없이 키로 병합.
        when(disasterEventRepository.findRegionalTypeMergeTarget(anyString(), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(Optional.of(20L));

        service.clusterNewAlert(ALERT_ID);

        ClusteringTrace trace = capturedTrace();
        assertThat(trace.getPath()).isEqualTo(ClusteringTracePath.REGIONAL);
        assertThat(trace.getResult()).isEqualTo(ClusteringTraceResult.MERGED);
        assertThat(trace.getEventId()).isEqualTo(20L);
        assertThat(trace.getCandidates()).isNull();
        assertThat(trace.getAdjacentCandidates()).isNull();
        assertThat(trace.getThreshold()).isNull();
        assertThat(trace.isLlmCalled()).isFalse();
    }

    // 결정: 비사건(ADVISORY)은 클러스터링에서 제외돼도 "제외됨"이라는 기록을 남긴다. 기록이 없으면
    //       대시보드가 이 알림을 "저장 실패"로 세어 버린다.
    // Red: 서비스가 upsert 를 호출하지 않아 "Wanted but not invoked" 로 실패한다.
    @Test
    @DisplayName("비사건(ADVISORY) 알림: path=SKIPPED, result=SKIPPED, eventId=null 로 기록")
    void advisoryAlert_recordsSkipped() {
        DisasterAlert a = alert("호우", "호우주의보 발효. 저지대 침수에 유의하세요.");
        a.classifyAs(AlertKind.ADVISORY);

        service.clusterNewAlert(ALERT_ID);

        ClusteringTrace trace = capturedTrace();
        assertThat(trace.getPath()).isEqualTo(ClusteringTracePath.SKIPPED);
        assertThat(trace.getResult()).isEqualTo(ClusteringTraceResult.SKIPPED);
        assertThat(trace.getEventId()).isNull();
    }

    // 결정: 기록 저장이 실패해도 수집은 막지 않는다 - 클러스터링 결과(매핑 저장·위험도 이벤트 발행)는 그대로 나가야 한다.
    //       기록을 저장하다 터진 예외가 클러스터링 쪽으로 번지면 알림이 이벤트에 배정되지 못한다.
    // Red: 서비스가 upsert 를 아직 호출하지 않아 마지막 verify(upsert) 에서 "Wanted but not invoked" 로 실패한다
    //      (앞의 예외 없음·매핑 저장·이벤트 발행 단언은 지금도 통과한다).
    @Test
    @DisplayName("기록 저장이 예외를 던져도 clusterNewAlert 는 예외 없이 끝나고 매핑 저장·이벤트 발행은 그대로 일어난다")
    void traceSaveFailure_doesNotAffectClusteringResult() {
        alert("산불", WILDFIRE);
        when(disasterEventRepository.findRegionalTypeMergeTarget(anyString(), any(String[].class), any(LocalDateTime.class)))
                .thenReturn(Optional.of(20L));
        // DB 장애를 흉내낸다 - 운영에서는 REQUIRES_NEW 트랜잭션 시작/SQL 단계에서 날 수 있는 예외다.
        doThrow(new DataAccessResourceFailureException("x")).when(clusteringTraceRepository).upsert(any());

        assertDoesNotThrow(() -> service.clusterNewAlert(ALERT_ID));

        verify(eventAlertMappingRepository).save(any());
        verify(eventPublisher).publishEvent(any(Object.class));
        verify(clusteringTraceRepository).upsert(any());
    }
}
