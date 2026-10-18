package com.disaster.alert.alertapi.domain.notification.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationTypeTest {

    @Test
    @DisplayName("NotificationType 은 NONE, PUSH 두 값만 가진다")
    void values_onlyNoneAndPush() {
        assertThat(NotificationType.values())
                .hasSize(2)
                .containsExactlyInAnyOrder(NotificationType.NONE, NotificationType.PUSH);
    }

    @Test
    @DisplayName("제거된 ALARM 값은 valueOf 로 조회할 수 없다")
    void valueOf_alarm_throws() {
        assertThatThrownBy(() -> NotificationType.valueOf("ALARM"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
