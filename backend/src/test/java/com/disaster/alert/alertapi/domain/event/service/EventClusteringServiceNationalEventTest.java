package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.AlertKind;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import com.disaster.alert.alertapi.domain.event.model.MergeMethod;
import com.disaster.alert.alertapi.domain.event.model.NationalScheduledEvent;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 전국 예정 행사(수능·민방위훈련) 경로 — 비사건(ADVISORY)이어도 행사 하나의 전국 광역 이벤트로 묶는다.
 *
 * <p>같은 행사는 이벤트 제목이 아니라 "행사 종류 키"(scheduled_event)로 찾는다. 제목은 화면 값이라 손으로 고치면
 * (운영 35605 를 "2026 수능"으로 바꾼 일) 제목 일치로는 같은 행사를 못 찾기 때문이다(2026-11-20 사용자 결정).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventClusteringServiceNationalEventTest {

    private static final String SUNEUNG_TITLE = "2027학년도 수능";

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

    /** 운영에 실제로 온 수능 시작 안내(기타 유형) — LLM 이 비사건(ADVISORY)으로 분류한 상태로 만든다. */
    private DisasterAlert advisorySuneungAlert() {
        DisasterAlert alert = DisasterAlert.builder().sn(200L).disasterType("기타")
                .message("(TEST ALERT) 잠시 후 2027학년도 대학수학능력시험이 시작됩니다. 공정한 시험운영을 위한 협조를 부탁드립니다. [교육부]")
                .createdAt(LocalDateTime.of(2026, 11, 19, 8, 0)).build();
        ReflectionTestUtils.setField(alert, "id", 1L);
        alert.classifyAs(AlertKind.ADVISORY);
        when(disasterAlertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(disasterEventRepository.save(any(DisasterEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        return alert;
    }

    // 결정: 수능 문자는 비사건이어도 비사건 제외(ADVISORY return)보다 먼저 전국 예정 행사 경로를 타서,
    //       같은 "행사 종류"(SUNEUNG)의 진행 중 광역 이벤트에 NATIONAL_EVENT 로 병합된다. 이벤트 제목은 보지 않는다.
    //       이유: 제목은 화면 값이라 손으로 고쳐도(예: "2026 수능") 같은 행사로 찾혀야 한다.
    // Red: 뼈대는 아직 행사 종류 자리에 제목("2027학년도 수능")을 넘겨 eq("SUNEUNG") 스텁이 응답하지 않는다.
    //      그래서 이벤트 77 을 못 찾고 신규 이벤트로 가 eventId 단언이 실패한다.
    @Test
    @DisplayName("ADVISORY 수능 알림은 제목이 바뀐 사건이어도 행사 종류(SUNEUNG)로 찾아 NATIONAL_EVENT 로 병합된다")
    void advisorySuneungAlertMergesIntoRunningNationalEventByKind() {
        // 준비: 이틀 전 예고 문자로 만들어져 아직 72시간 안인 수능 광역 이벤트(id 77, 알림 3건).
        //       제목은 손으로 "2026 수능"으로 바꿨다고 가정 — 알림이 만들어 줄 제목("2027학년도 수능")과 다르다.
        DisasterAlert alert = advisorySuneungAlert();
        DisasterEvent running = DisasterEvent.builder().eventTitle("2026 수능").primaryDisasterType("기타")
                .broadcast(true).alertCount(3).cooldownHours(72)
                .firstAlertAt(LocalDateTime.of(2026, 11, 17, 9, 0))
                .lastAlertAt(LocalDateTime.of(2026, 11, 18, 9, 0)).build();
        ReflectionTestUtils.setField(running, "id", 77L);
        // 병합 대상 조회는 "행사 종류"(enum 이름 SUNEUNG)로만 한다 — 키가 어긋나면 이 목은 응답하지 않아 신규 이벤트가 만들어진다.
        when(disasterEventRepository.findLatestNationalScheduledEvent(eq("SUNEUNG"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Optional.of(running));
        when(disasterEventRepository.findById(77L)).thenReturn(Optional.of(running));

        // 실행: 비사건으로 분류된 수능 알림을 클러스터링한다.
        service.clusterNewAlert(1L);

        // 검증: 알림이 이벤트 77 에 NATIONAL_EVENT 방식으로 들어갔다(비사건이라 버려지지 않았고, 제목이 달라도 찾았다).
        assertThat(alert.getEventId()).isEqualTo(77L);
        assertThat(alert.getEventMergeMethod()).isEqualTo(MergeMethod.NATIONAL_EVENT);
    }

    // 결정: 진행 중인 행사 이벤트가 없으면(첫 문자이거나 72시간을 넘긴 뒤) 신규 "전국 광역" 이벤트를 만들고,
    //       그 이벤트에 행사 종류 키(SUNEUNG)를 달아 다음 알림이 찾을 수 있게 한다. 제목은 화면용으로만 정한다.
    // Red: 뼈대의 markScheduledEvent 는 아무것도 하지 않아 저장된 이벤트의 scheduledEvent 가 null 이다.
    @Test
    @DisplayName("진행 중인 행사 이벤트가 없으면 행사 종류 키(SUNEUNG)가 달린 신규 전국 광역 이벤트를 만든다")
    void createsNationalEventWithKindKeyWhenNoneIsRunning() {
        // 준비: 72시간 안의 같은 행사 이벤트가 없는 상황(조회가 빈 값을 돌려준다).
        advisorySuneungAlert();
        when(disasterEventRepository.findLatestNationalScheduledEvent(any(), any(), any())).thenReturn(Optional.empty());

        // 실행
        service.clusterNewAlert(1L);

        // 검증: 저장된 이벤트가 광역이고, 행사 종류 키가 SUNEUNG 이며, 제목은 화면용 행사 제목이다.
        ArgumentCaptor<DisasterEvent> saved = ArgumentCaptor.forClass(DisasterEvent.class);
        verify(disasterEventRepository).save(saved.capture());
        assertThat(saved.getValue().isBroadcast()).isTrue();
        assertThat(saved.getValue().getScheduledEvent()).isEqualTo(NationalScheduledEvent.SUNEUNG);
        assertThat(saved.getValue().getEventTitle()).isEqualTo(SUNEUNG_TITLE);
    }

    // 결정: 행사 경로도 임베딩을 "저장만" 한다 — 병합/신규 판단은 행사 종류 키가 하고 임베딩은 판단에 쓰지 않는다.
    //       저장해 두면 00:30 구조 검사가 이 알림을 "임베딩 없음"(장애 신호)으로 세지 않는다.
    //       행사 이벤트는 광역(is_broadcast=true)이라 다른 유형의 임베딩 후보 검색에 안 걸려 묶음 결과는 안 바뀐다.
    // Red: 뼈대는 행사 경로에서 임베딩을 만들지 않아 updateEmbedding 이 한 번도 불리지 않는다.
    @Test
    @DisplayName("행사 경로에서도 임베딩을 저장하고, 병합 판단에는 쓰지 않는다")
    void nationalEventPathStoresEmbeddingWithoutUsingItForDecision() {
        // 준비: 위 병합 시나리오와 같은 진행 중 수능 이벤트(77). 이 알림에는 저장된 임베딩이 아직 없다.
        DisasterAlert alert = advisorySuneungAlert();
        DisasterEvent running = DisasterEvent.builder().eventTitle(SUNEUNG_TITLE).primaryDisasterType("기타")
                .broadcast(true).alertCount(3).cooldownHours(72)
                .firstAlertAt(LocalDateTime.of(2026, 11, 17, 9, 0))
                .lastAlertAt(LocalDateTime.of(2026, 11, 18, 9, 0)).build();
        ReflectionTestUtils.setField(running, "id", 77L);
        when(disasterEventRepository.findLatestNationalScheduledEvent(eq("SUNEUNG"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Optional.of(running));
        when(disasterEventRepository.findById(77L)).thenReturn(Optional.of(running));
        when(alertEmbeddingRepository.findEmbeddingText(1L)).thenReturn(null);
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});

        // 실행
        service.clusterNewAlert(1L);

        // 검증 1: 임베딩이 이 알림의 것으로 저장됐다.
        verify(alertEmbeddingRepository).updateEmbedding(eq(1L), anyString());
        // 검증 2: 병합은 행사 종류 키가 정한 그대로다 — 임베딩 후보 검색으로 판단이 바뀌지 않는다.
        assertThat(alert.getEventId()).isEqualTo(77L);
        assertThat(alert.getEventMergeMethod()).isEqualTo(MergeMethod.NATIONAL_EVENT);
    }
}
