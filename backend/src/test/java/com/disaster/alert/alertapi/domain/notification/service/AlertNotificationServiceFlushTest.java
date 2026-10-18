package com.disaster.alert.alertapi.domain.notification.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlertRegion;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlertRegionId;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.member.model.MemberFavoriteRegion;
import com.disaster.alert.alertapi.domain.member.repository.MemberFavoriteRegionRepository;
import com.disaster.alert.alertapi.domain.notification.dto.MemberToken;
import com.disaster.alert.alertapi.domain.notification.repository.FcmTokenRepository;
import com.disaster.alert.alertapi.domain.notification.repository.GuestFcmRegionRepository;
import com.disaster.alert.alertapi.domain.notification.repository.NotificationPreferenceRepository;
import com.disaster.alert.alertapi.domain.notification.repository.UserNotificationLogRepository;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.disaster.alert.alertapi.domain.notification.model.GuestFcmRegion;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 회원 팬아웃 중 영속성 컨텍스트 flush(= UserNotificationLog 배치 INSERT) 가 실패해도
 * FCM 발송이 중단되지 않아야 한다는 계약을 검증하는 순수 단위 테스트.
 */
class AlertNotificationServiceFlushTest {

    private static final long ALERT_ID = 1L;
    private static final String REGION_CODE = "1168010100";

    private MemberFavoriteRegionRepository favoriteRegionRepository;
    private NotificationPreferenceRepository preferenceRepository;
    private FcmTokenRepository fcmTokenRepository;
    private GuestFcmRegionRepository guestFcmRegionRepository;
    private UserNotificationLogRepository notificationLogRepository;
    private FcmSendService fcmSendService;
    private DisasterAlertRepository disasterAlertRepository;
    private EntityManager entityManager;

    private AlertNotificationService service;

    private ch.qos.logback.classic.Logger serviceLogger;
    private ListAppender<ILoggingEvent> logAppender;
    private Level originalLevel;

    @AfterEach
    void detachLogAppender() {
        if (serviceLogger != null) {
            serviceLogger.detachAppender(logAppender);
            serviceLogger.setLevel(originalLevel);
        }
    }

    @BeforeEach
    void setUp() {
        favoriteRegionRepository = mock(MemberFavoriteRegionRepository.class);
        preferenceRepository = mock(NotificationPreferenceRepository.class);
        fcmTokenRepository = mock(FcmTokenRepository.class);
        guestFcmRegionRepository = mock(GuestFcmRegionRepository.class);
        notificationLogRepository = mock(UserNotificationLogRepository.class);
        fcmSendService = mock(FcmSendService.class);
        disasterAlertRepository = mock(DisasterAlertRepository.class);
        entityManager = mock(EntityManager.class);

        service = new AlertNotificationService(
                favoriteRegionRepository, preferenceRepository, fcmTokenRepository,
                guestFcmRegionRepository, notificationLogRepository, fcmSendService,
                disasterAlertRepository);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
    }

    private void givenMembers(int count) {
        DisasterAlertRegionId regionId = mock(DisasterAlertRegionId.class);
        when(regionId.getDistrictCode()).thenReturn(REGION_CODE);
        DisasterAlertRegion region = mock(DisasterAlertRegion.class);
        when(region.getId()).thenReturn(regionId);
        DisasterAlert alert = mock(DisasterAlert.class);
        when(alert.getDisasterAlertRegions()).thenReturn(List.of(region));
        when(alert.getDisasterType()).thenReturn("호우");
        when(alert.getMessage()).thenReturn("테스트 본문");
        when(disasterAlertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert));

        List<Long> ids = LongStream.rangeClosed(1, count).boxed().toList();
        when(favoriteRegionRepository.findByIdLegalDistrictCodeIn(anyList()))
                .thenReturn(ids.stream()
                        .map(id -> MemberFavoriteRegion.of(id, REGION_CODE))
                        .collect(Collectors.toList()));
        when(preferenceRepository.findNotificationTypesByMemberIdIn(anyList())).thenReturn(List.of());
        when(fcmTokenRepository.findTokensByMemberIdIn(anyList()))
                .thenReturn(ids.stream()
                        .map(id -> new MemberToken(id, "token-" + id))
                        .toList());
        when(fcmSendService.sendToToken(anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn(true);
    }

    private void verifyAllMembersAttemptedAndGuestCalled(int count) {
        verify(fcmSendService, times(count))
                .sendToToken(anyString(), anyString(), any(), anyString(), anyString());
        verify(guestFcmRegionRepository).findAllByLegalDistrictCodeIn(anyList());
    }

    @Test
    @DisplayName("주기 flush 가 실패해도 남은 회원과 게스트에게 계속 발송한다")
    void 주기_flush_실패해도_발송_계속() {
        givenMembers(1_200);
        doThrow(new RuntimeException("batch insert 실패"))
                .doNothing()
                .when(entityManager).flush();

        assertThatCode(() -> service.triggerNotification(ALERT_ID)).doesNotThrowAnyException();

        verifyAllMembersAttemptedAndGuestCalled(1_200);
        // 실패한 flush 직후에도 영속성 컨텍스트를 비워 managed 엔티티 누적을 막는다
        verify(entityManager, org.mockito.Mockito.atLeastOnce()).clear();
    }

    @Test
    @DisplayName("루프 종료 후 마지막 잔여분(500 미만)을 명시적으로 flush 한다")
    void 마지막_잔여분_명시적_flush() {
        givenMembers(1_200);

        service.triggerNotification(ALERT_ID);

        // 500, 1000 시점 + 잔여 200건
        verify(entityManager, times(3)).flush();
    }

    @Test
    @DisplayName("회원 수가 500의 배수면 잔여분 flush 를 추가로 하지 않는다")
    void 오백의_배수는_추가_flush_없음() {
        givenMembers(1_000);

        service.triggerNotification(ALERT_ID);

        verify(entityManager, times(2)).flush();
    }

    @Test
    @DisplayName("마지막 잔여분 flush 가 실패해도 예외를 밖으로 던지지 않고 게스트 발송까지 진행한다")
    void 마지막_잔여분_flush_실패도_삼킴() {
        givenMembers(700);
        // 1번째 flush(500건 시점)는 성공, 2번째(잔여 200건)에서 실패
        doNothing().doThrow(new RuntimeException("잔여분 batch insert 실패"))
                .when(entityManager).flush();

        assertThatCode(() -> service.triggerNotification(ALERT_ID)).doesNotThrowAnyException();

        verifyAllMembersAttemptedAndGuestCalled(700);
        verify(entityManager, times(2)).flush();
    }

    private static final String GUEST_TOKEN = "guest-token";

    private void givenGuestToken() {
        when(guestFcmRegionRepository.findAllByLegalDistrictCodeIn(anyList()))
                .thenReturn(List.of(GuestFcmRegion.builder()
                        .fcmToken(GUEST_TOKEN).legalDistrictCode(REGION_CODE).build()));
    }

    private void captureServiceLogs() {
        serviceLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AlertNotificationService.class);
        originalLevel = serviceLogger.getLevel();
        serviceLogger.setLevel(Level.ALL);
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
    }

    /** "flush 실패" 문구가 든 로그의 레벨을 발생 순서대로 반환한다. */
    private List<Level> flushFailureLevels() {
        return logAppender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("flush 실패"))
                .map(ILoggingEvent::getLevel)
                .toList();
    }

    @Test
    @DisplayName("게스트 토큰 조회는 회원 팬아웃의 첫 flush 보다 먼저 일어난다")
    void 게스트_토큰_조회가_회원_flush_보다_먼저() {
        givenMembers(1_200);
        givenGuestToken();

        service.triggerNotification(ALERT_ID);

        InOrder order = inOrder(guestFcmRegionRepository, entityManager);
        order.verify(guestFcmRegionRepository).findAllByLegalDistrictCodeIn(anyList());
        order.verify(entityManager, org.mockito.Mockito.atLeastOnce()).flush();
    }

    @Test
    @DisplayName("게스트 발송은 모든 회원 발송이 끝난 뒤에 일어난다")
    void 게스트_발송은_회원_발송_뒤() {
        givenMembers(1_200);
        givenGuestToken();

        service.triggerNotification(ALERT_ID);

        InOrder order = inOrder(fcmSendService);
        // 마지막 회원 발송 -> 게스트 발송 순서. 회원 발송 1,200건 전부가 게스트보다 앞서야 한다
        order.verify(fcmSendService, times(1_200))
                .sendToToken(org.mockito.ArgumentMatchers.startsWith("token-"),
                        anyString(), any(), anyString(), anyString());
        order.verify(fcmSendService)
                .sendToToken(eq(GUEST_TOKEN), anyString(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("flush 가 매번 실패해도 ERROR 는 첫 실패 1건뿐이고 나머지는 WARN 이다")
    void flush_연속_실패는_첫_건만_ERROR() {
        givenMembers(1_500);
        captureServiceLogs();
        doThrow(new RuntimeException("batch insert 실패")).when(entityManager).flush();

        service.triggerNotification(ALERT_ID);

        verify(entityManager, times(3)).flush();
        assertThat(flushFailureLevels()).containsExactly(Level.ERROR, Level.WARN, Level.WARN);
    }

    @Test
    @DisplayName("flush 가 한 번 성공한 뒤 처음 실패하는 건도 ERROR 이고 이후는 WARN 이다")
    void 성공_뒤_첫_실패도_ERROR() {
        givenMembers(1_500);
        captureServiceLogs();
        doNothing().doThrow(new RuntimeException("batch insert 실패")).when(entityManager).flush();

        service.triggerNotification(ALERT_ID);

        verify(entityManager, times(3)).flush();
        assertThat(flushFailureLevels()).containsExactly(Level.ERROR, Level.WARN);
    }

    private static org.mockito.stubbing.Stubber doNothing() {
        return org.mockito.Mockito.doNothing();
    }
}
