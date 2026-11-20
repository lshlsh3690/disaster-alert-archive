package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 일반 전국 광역 병합({@code findLatestBroadcastNationwide})이 전국 예정 행사 이벤트(scheduled_event 가 있는 이벤트)를
 * 후보에서 빼는지 검증한다. 예전에는 제목 목록(excludedTitles)으로 뺐는데, 제목은 화면 값이라 손으로 고치면 제외가 풀렸다.
 * 이제는 행사 종류 컬럼이 NULL 인 이벤트만 후보로 삼는다.
 *
 * <p>실제 SQL(조건 + ORDER BY + LIMIT 1)이 핵심이라 DB 가 필요하다. 테스트마다 트랜잭션이 롤백되므로 넣은 데이터는 남지 않는다.
 * 쿼리가 "가장 최근 1개"를 고르므로 로컬 DB 의 실제 이벤트가 섞이면 우리 이벤트가 밀린다 — 그래서 날짜를 실제 데이터보다
 * 항상 뒤인 2999년으로 잡고, 결과가 비었는지가 아니라 "우리가 넣은 이벤트 중 무엇이 결과인지"로 단언한다.
 */
@IntegrationTest
@Transactional
class DisasterEventRepositoryNationalScheduledEventTest {

    private static final long DRILL_EVENT_ID = 9_300_000_001L;
    private static final long ORDINARY_EVENT_ID = 9_300_000_002L;
    private static final LocalDateTime SINCE = LocalDateTime.of(2999, 3, 1, 0, 0);

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    DisasterEventRepository repository;

    // 결정: 행사 이벤트가 기간 안 가장 최근이어도 일반 전국 광역 병합은 그것을 고르지 않고, 그 아래 일반 전국 민방공 이벤트를 고른다.
    //       제외는 1건만 고르기(LIMIT 1) 전에 해야 한다 — 고른 뒤에 거르면 실제 경보 이벤트가 후보에 오르지 못해 후속 알림이 갈라진다.
    // Red: 뼈대 SQL 에는 scheduled_event 조건이 없어 가장 최근인 행사 이벤트(DRILL_EVENT_ID)가 그대로 선택돼 단언이 실패한다.
    @Test
    @DisplayName("전국 광역 병합은 더 최근인 행사 이벤트를 건너뛰고 일반 전국 민방공 이벤트를 고른다")
    void 전국_광역_병합은_행사_이벤트를_건너뛴다() {
        // 준비: 둘 다 전국 광역(is_broadcast, 지역 코드 없음) 민방공 이벤트.
        //       행사 이벤트(훈련)가 3/4 로 더 최근이고, 일반 전국 경보 이벤트는 3/3 이다.
        seedNationwideEvent(DRILL_EVENT_ID, "CIVIL_DEFENSE_DRILL", "2999-03-04 10:00:00");
        seedNationwideEvent(ORDINARY_EVENT_ID, null, "2999-03-03 10:00:00");

        // 실행: 일반 광역 병합이 민방공 전국 이벤트를 찾는다.
        Optional<DisasterEvent> found = repository.findLatestBroadcastNationwide("민방공", SINCE);

        // 검증: 결과는 일반 전국 경보 이벤트다 — 행사 이벤트가 골라지면 실제 전국 경보가 훈련 이벤트에 묻힌다.
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(ORDINARY_EVENT_ID);
    }

    /** 전국 광역 민방공 이벤트 하나를 넣는다. {@code scheduledEvent} 가 null 이면 일반 이벤트, 값이 있으면 행사 이벤트. */
    private void seedNationwideEvent(long id, String scheduledEvent, String lastAlertAt) {
        jdbc.update("INSERT INTO disaster_events(id, event_title, primary_disaster_type, primary_region_code, "
                        + "first_alert_at, last_alert_at, alert_count, is_broadcast, scheduled_event) "
                        + "VALUES (?, '행사 키 테스트 이벤트', '민방공', NULL, CAST(? AS timestamp), CAST(? AS timestamp), 1, true, ?)",
                id, lastAlertAt, lastAlertAt, scheduledEvent);
    }
}
