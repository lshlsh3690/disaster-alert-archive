package com.disaster.alert.alertapi.domain.event.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DisasterEvent#addAlert(LocalDateTime)} 검증 — 알림이 이벤트에 병합될 때 이벤트가 스스로
 * 알림 수와 마지막 알림 시각을 갱신하고, 새 알림이 이벤트 안에서 몇 번째인지(순번)를 돌려준다.
 *
 * <p>순수 JUnit 단위 테스트(DB·Spring 컨텍스트 없음).
 */
class DisasterEventTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 7, 20, 12, 0);

    /** 알림 3건이 쌓였고 마지막 알림이 T 인 이벤트. */
    private DisasterEvent eventWithThreeAlerts() {
        return DisasterEvent.builder()
                .primaryDisasterType("화재")
                .firstAlertAt(T.minusHours(5))
                .lastAlertAt(T)
                .alertCount(3)
                .build();
    }

    // 결정: 병합 순번은 DB 에서 알림을 세는(count) 대신 이벤트의 alertCount 를 올린 값으로 매긴다.
    //       그래서 addAlert 의 반환값이 곧 새 알림의 순번(올린 뒤의 alertCount)이어야 한다.
    //       더 늦게 온 알림이면 마지막 알림 시각도 그 시각으로 앞당겨 진행 중 판정(cooldown)이 이어지게 한다.
    // Red: 뼈대가 0 을 돌려주고 아무것도 갱신하지 않아 "expected 4 but was 0" 으로 실패한다.
    @Test
    @DisplayName("더 늦은 알림 병합: 알림 수가 1 늘고 순번(4)을 반환하며 마지막 알림 시각이 그 알림 시각으로 갱신된다")
    void addAlert_laterAlert_incrementsCountAndAdvancesLastAlertAt() {
        // 준비: 알림 3건(마지막 T) 이벤트에, T+1h 에 온 네 번째 알림을 넣는 상황
        DisasterEvent event = eventWithThreeAlerts();
        LocalDateTime later = T.plusHours(1);

        // 실행
        int seq = event.addAlert(later);

        // 검증: 기존 3건 + 1건 = 4 번째 알림, 이벤트 알림 수도 4
        assertThat(seq).isEqualTo(4);
        assertThat(event.getAlertCount()).isEqualTo(4);
        // 검증: 새 알림이 가장 늦으므로 마지막 알림 시각이 T+1h 로 옮겨간다
        assertThat(event.getLastAlertAt()).isEqualTo(later);
    }

    // 결정: 수집 순서가 뒤바뀌어 더 이른 시각의 알림이 나중에 병합될 수 있다(재처리·백필 포함).
    //       이때 마지막 알림 시각을 과거로 되돌리면 이벤트가 실제보다 일찍 종료된 것으로 파생되므로 max 로 유지한다.
    //       알림 수와 순번은 시각과 무관하게 올라간다.
    // Red: 뼈대가 0 을 돌려주고 알림 수도 올리지 않아 순번 단언에서 "expected 4 but was 0" 으로 실패한다.
    @Test
    @DisplayName("더 이른 알림 병합: 알림 수와 순번은 오르지만 마지막 알림 시각은 그대로(T) 유지된다")
    void addAlert_earlierAlert_incrementsCountButKeepsLastAlertAt() {
        // 준비: 마지막 알림이 T 인 이벤트에 T-1h 의 알림(이미 지난 시각)을 넣는 상황
        DisasterEvent event = eventWithThreeAlerts();

        // 실행
        int seq = event.addAlert(T.minusHours(1));

        // 검증: 시각이 이르더라도 병합된 순서상 네 번째이므로 알림 수·순번은 4
        assertThat(seq).isEqualTo(4);
        assertThat(event.getAlertCount()).isEqualTo(4);
        // 검증: 마지막 알림 시각은 더 늦은 기존 값 T 가 유지된다(과거로 되돌리지 않음)
        assertThat(event.getLastAlertAt()).isEqualTo(T);
    }
}
