package com.disaster.alert.alertapi.domain.notification.service;

import com.disaster.alert.alertapi.domain.notification.repository.FcmTokenRepository;
import com.disaster.alert.alertapi.domain.notification.repository.GuestFcmRegionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link DeadTokenCleanupService#cleanUp} 순수 단위 테스트.
 * DB·Spring 컨텍스트가 필요 없으므로 @SpringBootTest 없이 Mockito 목으로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeadTokenCleanupService 죽은 토큰 정리")
class DeadTokenCleanupServiceTest {

    @Mock
    private FcmTokenRepository fcmTokenRepository;

    @Mock
    private GuestFcmRegionRepository guestFcmRegionRepository;

    @InjectMocks
    private DeadTokenCleanupService deadTokenCleanupService;

    @Test
    @DisplayName("죽은 토큰 목록으로 회원 토큰 테이블과 게스트 지역 구독 테이블 양쪽을 모두 지운다 " +
            "— 한쪽만 지우면 게스트는 매 발송마다 같은 토큰으로 계속 실패하거나, 회원 발송 대상 수가 부풀어 보인다")
    void deletesFromBothRepositoriesWithSameTokenList() {
        List<String> deadTokens = List.of("dead-token-1", "dead-token-2");
        when(fcmTokenRepository.deleteAllByTokenIn(deadTokens)).thenReturn(1);
        when(guestFcmRegionRepository.deleteAllByFcmTokenIn(deadTokens)).thenReturn(1);

        deadTokenCleanupService.cleanUp(deadTokens);

        verify(fcmTokenRepository).deleteAllByTokenIn(deadTokens);
        verify(guestFcmRegionRepository).deleteAllByFcmTokenIn(deadTokens);
    }

    @Test
    @DisplayName("두 테이블에서 삭제된 행 수의 합을 반환한다 — fcm_token 2건 + guest_fcm_region 3건이면 5를 반환해야 한다")
    void returnsSumOfDeletedRowCounts() {
        List<String> deadTokens = List.of("dead-token-1", "dead-token-2");
        when(fcmTokenRepository.deleteAllByTokenIn(deadTokens)).thenReturn(2);
        when(guestFcmRegionRepository.deleteAllByFcmTokenIn(deadTokens)).thenReturn(3);

        int result = deadTokenCleanupService.cleanUp(deadTokens);

        assertThat(result).isEqualTo(5);
    }

    @Test
    @DisplayName("빈 목록이면 저장소를 아예 건드리지 않고 0을 반환한다 " +
            "— 발송 대부분은 죽은 토큰이 0개인 정상 경로라 이 분기가 가장 자주 탄다")
    void doesNotTouchRepositoriesWhenTokenListEmpty() {
        int result = deadTokenCleanupService.cleanUp(List.of());

        assertThat(result).isZero();
        verifyNoInteractions(fcmTokenRepository);
        verifyNoInteractions(guestFcmRegionRepository);
    }

    @Test
    @DisplayName("null이 전달되면 NPE 없이 0을 반환하고 저장소를 건드리지 않는다")
    void doesNotTouchRepositoriesWhenTokenListNull() {
        int result = deadTokenCleanupService.cleanUp(null);

        assertThat(result).isZero();
        verifyNoInteractions(fcmTokenRepository);
        verifyNoInteractions(guestFcmRegionRepository);
    }
}
