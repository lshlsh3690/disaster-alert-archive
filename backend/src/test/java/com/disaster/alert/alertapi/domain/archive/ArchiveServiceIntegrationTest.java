package com.disaster.alert.alertapi.domain.archive;

import com.disaster.alert.alertapi.domain.archive.dto.ArchiveEventResolveResponse;
import com.disaster.alert.alertapi.domain.archive.dto.ArchiveMajorEventsResponse;
import com.disaster.alert.alertapi.domain.archive.service.ArchiveService;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import com.disaster.alert.alertapi.domain.event.model.MergeMethod;
import com.disaster.alert.alertapi.domain.event.repository.DisasterEventRepository;
import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 재난 기록관의 핵심 규칙 4개를 실제 Postgres 로 검증한다.
 *
 * <p>규칙이 SQL 조회에 들어가므로(사건 전체를 메모리로 올려 거를 수 없다) 통합 테스트가 맞다.
 * 데이터는 실제 사건이 없는 1999년으로 넣고 테스트 트랜잭션과 함께 롤백된다 — 운영·개발 데이터와 섞이지 않는다.
 *
 */
@IntegrationTest
@Transactional
class ArchiveServiceIntegrationTest {

    /** 실제 사건이 없는 연도 — 다른 데이터가 결과에 섞이지 않게 한다. */
    private static final int YEAR = 1999;

    @Autowired
    private ArchiveService archiveService;
    @Autowired
    private DisasterEventRepository eventRepository;
    @Autowired
    private DisasterAlertRepository alertRepository;

    /** 테스트가 넣는 알림에 매기는 sn. 운영 sn 대역과 겹치지 않는 큰 값에서 시작해 하나씩 올린다. */
    private long nextSn = 990_000_000L;

    /**
     * 지속형 유형(폭염)도 유형으로 거르지 않는다 — 처음의 "지속형 제외" 규칙은 실측 근거가 없었고 평가 사본에서
     * 효과가 0이었다(research.md R3). 폭염 사건의 알림 수가 가장 많으면 목록 맨 앞에 와야 한다.
     * 현재 코드는 지속형을 SQL 에서 빼므로 폭염이 사라져 ["호우 사건"] 만 나와 순서 단언에서 실패한다.
     */
    @Test
    @DisplayName("지속형 유형(폭염) 사건도 알림 수가 많으면 주요 사건에 포함된다")
    void 지속형_유형도_알림_수가_많으면_주요_사건에_포함된다() {
        // 폭염 사건: 알림 5건으로 가장 크다 → 유형으로 거르지 않으면 1위가 된다
        saveEventWithAlerts("폭염 사건", "폭염", LocalDateTime.of(YEAR, 7, 1, 9, 0), 5);
        // 호우 사건: 알림 2건 → 폭염 뒤 2위
        saveEventWithAlerts("호우 사건", "호우", LocalDateTime.of(YEAR, 7, 10, 9, 0), 2);

        ArchiveMajorEventsResponse response = archiveService.majorEvents(YEAR);

        // 알림 수 내림차순이라 폭염(5) → 호우(2). 폭염이 빠져 있으면(현재 동작) 여기서 실패한다
        assertThat(response.events())
                .extracting(ArchiveMajorEventsResponse.Item::eventTitle)
                .containsExactly("폭염 사건", "호우 사건");
    }

    /**
     * 주요 사건은 포함 알림 수가 많은 순서로 최대 10개다. 11개를 넣어 "10개로 잘리고, 가장 작은 하나가 빠지는지"를 본다.
     * 뼈대는 빈 목록이라 알림 수 순서 단언에서 실패한다.
     */
    @Test
    @DisplayName("알림 수 내림차순으로 최대 10개만 나온다")
    void 알림_수_내림차순_최대_10개() {
        // 알림 수 1~11건인 호우 사건 11개 — 일부러 작은 수부터 넣어 "입력 순서가 아니라 알림 수로 정렬"하는지 본다
        for (int count = 1; count <= 11; count++) {
            saveEventWithAlerts("사건 " + count, "호우", LocalDateTime.of(YEAR, 3, count, 9, 0), count);
        }

        ArchiveMajorEventsResponse response = archiveService.majorEvents(YEAR);

        // 11개 중 10개만, 가장 큰 11부터 2까지. 알림 1건짜리(11번째)는 잘려 나간다
        assertThat(response.events())
                .extracting(ArchiveMajorEventsResponse.Item::alertCount)
                .containsExactly(11, 10, 9, 8, 7, 6, 5, 4, 3, 2);
    }

    /**
     * 사건 영구 링크의 기준(대표 sn)은 "사건에서 가장 먼저 발령된 알림"이다. 입력 순서·sn 크기가 아니라 발령 시각으로 정하고,
     * 같은 시각이면 작은 sn 이 이긴다. 뼈대는 빈 목록이라 대표 sn 단언에서 실패한다.
     */
    @Test
    @DisplayName("대표 sn 은 사건에서 가장 먼저 발령된 알림의 sn 이다(같은 시각이면 작은 sn)")
    void 대표_sn은_가장_먼저_발령된_알림() {
        DisasterEvent event = saveEvent("대표 알림 사건", "호우", LocalDateTime.of(YEAR, 5, 1, 10, 0), 4);
        // 가장 먼저 발령(10:00)된 알림이 둘 — sn 이 큰 쪽을 먼저 저장해 "작은 sn 이 이긴다"를 본다
        saveAlert(500L, LocalDateTime.of(YEAR, 5, 1, 10, 0), event);
        saveAlert(400L, LocalDateTime.of(YEAR, 5, 1, 10, 0), event);
        // sn 이 가장 작지만(100) 30분 늦게 발령 → 시각이 sn 보다 우선하므로 대표가 아니다
        saveAlert(100L, LocalDateTime.of(YEAR, 5, 1, 10, 30), event);
        saveAlert(600L, LocalDateTime.of(YEAR, 5, 1, 11, 0), event);

        ArchiveMajorEventsResponse response = archiveService.majorEvents(YEAR);

        // 10:00 알림 둘 중 작은 sn(400). 100 이면 sn 만 보고 고른 것, 500 이면 같은 시각 순서를 안 본 것이다
        assertThat(response.events())
                .extracting(ArchiveMajorEventsResponse.Item::representativeSn)
                .containsExactly(400L);
    }

    /**
     * 영구 링크는 사건 번호가 아니라 sn 으로 열린다. 사건에 묶인 알림이면 지금 속한 사건과 대표 sn 을 알려 주고
     * (대표가 아닌 알림의 sn 이어도 같은 사건), 어떤 사건에도 묶이지 않은 알림이면 bound=false 다.
     * 뼈대는 항상 bound=false 라 첫 번째(묶인 경우) 단언에서 실패한다.
     */
    @Test
    @DisplayName("sn 은 현재 속한 사건과 대표 sn 으로 해석되고, 사건에 안 묶인 알림은 bound=false 다")
    void sn을_현재_사건으로_해석한다() {
        DisasterEvent event = saveEvent("해석 사건", "호우", LocalDateTime.of(YEAR, 6, 1, 9, 0), 2);
        saveAlert(1000L, LocalDateTime.of(YEAR, 6, 1, 9, 0), event);     // 대표 알림
        saveAlert(2000L, LocalDateTime.of(YEAR, 6, 1, 12, 0), event);    // 사건에 합쳐진 뒤 알림 — 대표가 아니다
        saveAlert(3000L, LocalDateTime.of(YEAR, 6, 2, 9, 0), null);      // 사건에 묶이지 않은 알림

        // 대표가 아닌 알림(2000)으로 열어도 같은 사건으로 해석되고, 링크의 기준은 대표 sn(1000)이다
        ArchiveEventResolveResponse bound = archiveService.resolveEvent(2000L);
        assertThat(bound.bound()).isTrue();
        assertThat(bound.eventId()).isEqualTo(event.getId());
        assertThat(bound.representativeSn()).isEqualTo(1000L);

        // 묶이지 않은 알림은 오류가 아니라 정상 상태 — 사건 정보 없이 알림 요약만 내려간다
        ArchiveEventResolveResponse unbound = archiveService.resolveEvent(3000L);
        assertThat(unbound.bound()).isFalse();
        assertThat(unbound.eventId()).isNull();
        assertThat(unbound.alert()).isNotNull();
        assertThat(unbound.alert().sn()).isEqualTo(3000L);
    }

    /**
     * 공개 API 라 로그인 없이 누구나 ?year= 에 아무 숫자나 넣을 수 있다. 사건이 있는 연도 목록에 없는 연도는 DB 를 조회하지 않고
     * 빈 목록으로 답해야 한다 — 300000 년은 Postgres timestamp 범위(최대 294276년)를 넘어 DB 예외 → 500 + log.error 가 되는데,
     * 로그 레벨이 유일한 장애 신호라 이런 입력이 error 로그를 쌓으면 안 된다. 예외는 400 도 아니다(데이터 없는 연도와 같은 취급).
     * 현재 코드는 목록 확인 없이 [300000-01-01, 300001-01-01) 로 조회해 예외가 터지고, 그 예외가 단언(doesNotThrowAnyException)에서 실패로 잡힌다.
     */
    @Test
    @DisplayName("사건이 없는 터무니없는 연도(300000)는 예외 없이 빈 목록으로 답한다")
    void 범위_밖_연도는_예외_없이_빈_목록() {
        // 사건이 있는 연도(1999)를 하나 만들어 둔다 — years 는 비어 있지 않고 300000 은 그 안에 없다는 상황
        saveEventWithAlerts("호우 사건", "호우", LocalDateTime.of(YEAR, 7, 10, 9, 0), 2);

        // 예외가 그대로 터지면 "예외로 실패"가 되어 Red 로 읽기 어렵다 — 감싸서 "예외 없어야 한다"는 단언에서 실패하게 한다
        AtomicReference<ArchiveMajorEventsResponse> result = new AtomicReference<>();
        assertThatCode(() -> result.set(archiveService.majorEvents(300_000)))
                .doesNotThrowAnyException();

        // 응답 year 는 요청값 그대로, 사건이 없으니 events 는 빈 목록, years 는 실제 사건 연도(1999) 그대로
        assertThat(result.get().year()).isEqualTo(300_000);
        assertThat(result.get().events()).isEmpty();
        assertThat(result.get().years()).contains(YEAR);
    }

    // ── 테스트 데이터 ───────────────────────────────────────────

    /** 사건 하나와 알림 {@code alertCount} 건(1분 간격)을 만든다. 사건의 alert_count 도 같은 값이라 실제 데이터와 모순이 없다. */
    private DisasterEvent saveEventWithAlerts(String title, String type, LocalDateTime firstAlertAt, int alertCount) {
        DisasterEvent event = saveEvent(title, type, firstAlertAt, alertCount);
        for (int i = 0; i < alertCount; i++) {
            saveAlert(nextSn++, firstAlertAt.plusMinutes(i), event);
        }
        return event;
    }

    private DisasterEvent saveEvent(String title, String type, LocalDateTime firstAlertAt, int alertCount) {
        return eventRepository.saveAndFlush(DisasterEvent.builder()
                .eventTitle(title)
                .primaryDisasterType(type)
                .primaryRegionName("테스트도 테스트시")
                .firstAlertAt(firstAlertAt)
                .lastAlertAt(firstAlertAt.plusMinutes(alertCount))
                .alertCount(alertCount)
                .cooldownHours(72)
                .build());
    }

    /** 알림 한 건. {@code event} 가 null 이면 어떤 사건에도 묶이지 않은 알림이다. sn 은 호출자가 정한다. */
    private DisasterAlert saveAlert(long sn, LocalDateTime createdAt, DisasterEvent event) {
        DisasterAlert alert = DisasterAlert.builder()
                .sn(sn)
                .message("테스트 문자 " + sn)
                .disasterType(event == null ? "호우" : event.getPrimaryDisasterType())
                .createdAt(createdAt)
                .build();
        if (event != null) {
            alert.assignToEvent(event, 1, null, MergeMethod.SEED);
        }
        return alertRepository.saveAndFlush(alert);
    }
}
