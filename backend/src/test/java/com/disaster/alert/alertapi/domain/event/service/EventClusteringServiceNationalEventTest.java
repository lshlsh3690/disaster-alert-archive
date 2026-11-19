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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 전국 예정 행사(수능·민방위훈련) 경로 — 비사건(ADVISORY)이어도 행사 하나의 전국 광역 이벤트로 묶는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventClusteringServiceNationalEventTest {

    private static final String SUNEUNG_TITLE = "2026 수능";

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
    //       같은 제목("2026 수능")의 진행 중 광역 이벤트에 NATIONAL_EVENT 로 병합된다.
    //       이유: 수능 안내 문자는 대부분 비사건이라 이전에는 전부 미배정으로 버려졌다.
    // Red: 현재는 ADVISORY 면 바로 return 해서 알림이 어느 이벤트에도 배정되지 않아 eventId 단언이 실패한다.
    @Test
    @DisplayName("ADVISORY 수능 알림은 비사건 제외를 건너뛰고 같은 제목의 진행 중 광역 이벤트에 NATIONAL_EVENT 로 병합된다")
    void advisorySuneungAlertMergesIntoRunningNationalEvent() {
        // 준비: 이틀 전 예고 문자로 만들어져 아직 72시간 안인 수능 광역 이벤트(id 77, 알림 3건).
        DisasterAlert alert = advisorySuneungAlert();
        DisasterEvent running = DisasterEvent.builder().eventTitle(SUNEUNG_TITLE).primaryDisasterType("기타")
                .broadcast(true).alertCount(3).cooldownHours(72)
                .firstAlertAt(LocalDateTime.of(2026, 11, 17, 9, 0))
                .lastAlertAt(LocalDateTime.of(2026, 11, 18, 9, 0)).build();
        ReflectionTestUtils.setField(running, "id", 77L);
        // 병합 대상 조회는 "행사 제목"으로만 한다 — 제목이 어긋나면 이 목은 응답하지 않아 신규 이벤트가 만들어진다.
        when(disasterEventRepository.findLatestNationalScheduledEvent(eq(SUNEUNG_TITLE), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Optional.of(running));
        when(disasterEventRepository.findById(77L)).thenReturn(Optional.of(running));

        // 실행: 비사건으로 분류된 수능 알림을 클러스터링한다.
        service.clusterNewAlert(1L);

        // 검증 1: 알림이 이벤트 77 에 NATIONAL_EVENT 방식으로 들어갔다(비사건이라 버려지지 않았다).
        assertThat(alert.getEventId()).isEqualTo(77L);
        assertThat(alert.getEventMergeMethod()).isEqualTo(MergeMethod.NATIONAL_EVENT);
        // 검증 2: 임베딩(유료 호출)은 쓰지 않는다 — 행사 종류가 곧 키라 본문 유사도를 보지 않는다.
        verifyNoInteractions(embeddingModel);
    }

    // 결정: 진행 중인 행사 이벤트가 없으면(첫 문자이거나 72시간을 넘긴 뒤) 신규 "전국 광역" 이벤트를 만들고
    //       제목을 행사 제목으로 정한다. 지역 이벤트로 만들면 시군구가 따로 보낸 안내가 붙지 못한다.
    // Red: 비사건 제외로 return 해서 이벤트 저장이 한 번도 일어나지 않아 save 검증이 실패한다.
    @Test
    @DisplayName("진행 중인 행사 이벤트가 없으면 행사 제목의 신규 전국 광역 이벤트를 만든다")
    void createsNationalEventWhenNoneIsRunning() {
        // 준비: 72시간 안의 같은 행사 이벤트가 없는 상황(조회가 빈 값을 돌려준다).
        advisorySuneungAlert();
        when(disasterEventRepository.findLatestNationalScheduledEvent(any(), any(), any())).thenReturn(Optional.empty());

        // 실행
        service.clusterNewAlert(1L);

        // 검증: 저장된 이벤트가 광역이고 제목이 알림 연도의 "수능"이다(생성 뒤 제목을 행사 제목으로 정함).
        ArgumentCaptor<DisasterEvent> saved = ArgumentCaptor.forClass(DisasterEvent.class);
        verify(disasterEventRepository).save(saved.capture());
        assertThat(saved.getValue().isBroadcast()).isTrue();
        assertThat(saved.getValue().getEventTitle()).isEqualTo(SUNEUNG_TITLE);
    }
}
