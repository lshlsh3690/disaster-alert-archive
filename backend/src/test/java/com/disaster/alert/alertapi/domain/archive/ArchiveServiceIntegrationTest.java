package com.disaster.alert.alertapi.domain.archive;

import com.disaster.alert.alertapi.domain.archive.dto.ArchiveEventResolveResponse;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사건 영구 링크의 핵심 규칙(대표 sn, sn → 현재 사건 해석)을 실제 Postgres 로 검증한다.
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

    /**
     * 사건 영구 링크의 기준(대표 sn)은 "사건에서 가장 먼저 발령된 알림"이다. 입력 순서·sn 크기가 아니라 발령 시각으로 정하고,
     * 같은 시각이면 작은 sn 이 이긴다. 사건 상세의 영구 링크와 sn 해석이 같은 규칙을 쓴다.
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

        // 사건의 어느 알림으로 열어도(여기서는 가장 늦은 600) 대표 sn 은 같다
        ArchiveEventResolveResponse resolved = archiveService.resolveEvent(600L);

        // 10:00 알림 둘 중 작은 sn(400). 100 이면 sn 만 보고 고른 것, 500 이면 같은 시각 순서를 안 본 것이다
        assertThat(resolved.representativeSn()).isEqualTo(400L);
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

    // ── 테스트 데이터 ───────────────────────────────────────────

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
