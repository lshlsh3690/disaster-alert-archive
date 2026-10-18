package com.disaster.alert.alertapi.domain.notification.tool;

import com.disaster.alert.alertapi.domain.notification.service.AlertNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 알림 팬아웃({@link AlertNotificationService#triggerNotification}) 성능 측정용 수동 트리거 도구.
 *
 * <p><b>활성 조건</b>: Spring Profile {@code fanout-trigger} 활성 시에만 실행.
 * 일반 dev / 운영 부팅에서는 빈 등록 자체가 안 됨.
 *
 * <p><b>배경</b>: 이 역할을 하던 관리자 API(옛 {@code POST /api/v1/admin/trigger-notification/{alertId}})는
 * FCM 알림 E2E 테스트 목적을 다해 제거됐다({@code AdminController}). 측정 하네스
 * ({@code backend/loadtest/seed/seed_notification_data.sql})는 트리거용 {@code disaster_alert}
 * 1건을 실행마다 새로 만들어 {@code disaster_alert_id >= 900000000} 대역에 쌓아두므로, 그중
 * 가장 최근 것을 자동으로 찾아 트리거한다 — alertId를 따로 넘길 필요가 없다.
 *
 * <p><b>사용법</b>
 * <pre>
 * docker exec -i postgres psql -U $POSTGRES_USER -d $POSTGRES_DB &lt; backend/loadtest/seed/seed_notification_data.sql
 * export SPRING_PROFILES_ACTIVE=fanout-trigger
 * cd backend &amp;&amp; set -a &amp;&amp; source ../.env.dev &amp;&amp; set +a
 * ./gradlew bootRun --args='--fcm.dry-run=true'
 * # 로그에서 "회원 팬아웃 완료 - alertId: ..., time=Xms" 확인
 * </pre>
 */
@Component
@Profile("fanout-trigger")
@RequiredArgsConstructor
@Slf4j
public class NotificationFanoutTriggerTool implements ApplicationRunner {

    private final AlertNotificationService alertNotificationService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Long alertId = findLatestSeededAlertId();
        if (alertId == null) {
            log.warn("fanout-trigger: seed_notification_data.sql로 만든 트리거용 alert가 없음 — 먼저 시드부터 실행할 것");
            return;
        }
        log.info("fanout-trigger: alertId={} 트리거 시작", alertId);
        alertNotificationService.triggerNotification(alertId);
    }

    private Long findLatestSeededAlertId() {
        String sql = "SELECT disaster_alert_id FROM disaster_alert "
                + "WHERE disaster_alert_id >= 900000000 "
                + "ORDER BY disaster_alert_id DESC LIMIT 1";
        List<Long> result = jdbcTemplate.queryForList(sql, Long.class);
        return result.isEmpty() ? null : result.get(0);
    }
}
