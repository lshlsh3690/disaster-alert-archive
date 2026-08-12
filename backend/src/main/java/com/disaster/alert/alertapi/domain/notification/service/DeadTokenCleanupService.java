package com.disaster.alert.alertapi.domain.notification.service;

import com.disaster.alert.alertapi.domain.notification.repository.FcmTokenRepository;
import com.disaster.alert.alertapi.domain.notification.repository.GuestFcmRegionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * FCM 이 영구 무효로 판정한 토큰을 저장소에서 걷어낸다.
 *
 * <p>토큰이 죽는 경로는 회원과 게스트 둘 다다. 회원은 {@code fcm_token} 행 하나가 토큰을 들고
 * 있고, 게스트는 토큰 자체가 신원이라 {@code guest_fcm_region} 의 지역 구독들이 토큰에 매달려
 * 있다. <b>둘 중 하나만 지우면 죽은 토큰이 반쪽만 사라진다</b> — 게스트 쪽을 빠뜨리면 매 발송마다
 * 같은 토큰으로 계속 실패하고, 회원 쪽을 빠뜨리면 발송 대상 수가 실제보다 부풀어 보인다.
 * 그래서 이 컴포넌트는 항상 양쪽을 함께 지운다.
 *
 * <p><b>전파는 {@code REQUIRES_NEW} 다 — 발송 트랜잭션에 참여시키면 안 된다.</b> 호출부인
 * {@code AlertNotificationService} 는 {@code triggerNotification}/{@code sendToMember}/
 * {@code sendToGuestTokens} 세 겹 모두 {@code catch (Exception)} 으로 예외를 삼키고 발송을
 * 계속한다. 기본 전파였다면 정리 실패가 발송 트랜잭션을 rollback-only 로 표시하고, 삼켜진
 * 예외 때문에 그 사실을 아무도 모른 채 커밋 시점에 {@code UnexpectedRollbackException} 이
 * 터진다 — 토큰 정리 실패 하나로 그 알림의 발송 이력 전체가 되돌아가고, 원인은 로그상
 * 한참 떨어진 곳에 나타난다. 별도 트랜잭션으로 떼어내 정리 실패가 발송에 번지지 않게 한다.
 *
 * <p>{@code REQUIRES_NEW} 의 통상적인 위험인 자기 교착(바깥 트랜잭션이 잠근 행을 안쪽이
 * 기다리는 상황)은 여기서는 없다. 발송 경로는 {@code fcm_token}/{@code guest_fcm_region} 을
 * <b>읽기만</b> 하고 수정하지 않아 그 행들에 쓰기 잠금을 걸지 않는다. 이 전제가 깨지면
 * (발송 중 토큰 행을 UPDATE 하게 되면) 교착이 생기므로 그때 이 결정을 다시 봐야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeadTokenCleanupService {

    private final FcmTokenRepository fcmTokenRepository;
    private final GuestFcmRegionRepository guestFcmRegionRepository;

    /**
     * 죽은 토큰들을 회원 토큰 테이블과 게스트 지역 구독 테이블 양쪽에서 삭제한다.
     *
     * <p>빈 목록/null 이면 <b>저장소를 건드리지 않는다</b>. 발송 대부분은 죽은 토큰이 하나도
     * 없는 정상 경로라 이 가드가 실제로는 가장 자주 타는 분기이고, 빈 목록으로 {@code in ()}
     * 쿼리를 날리는 낭비를 막는다.
     *
     * @return 두 테이블에서 삭제된 행 수의 합
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int cleanUp(List<String> deadTokens) {
        if (deadTokens == null || deadTokens.isEmpty()) {
            return 0;
        }

        int deletedTokenCount = fcmTokenRepository.deleteAllByTokenIn(deadTokens);
        int deletedGuestRegionCount = guestFcmRegionRepository.deleteAllByFcmTokenIn(deadTokens);
        int totalDeleted = deletedTokenCount + deletedGuestRegionCount;

        if (totalDeleted > 0) {
            log.info("죽은 FCM 토큰 정리 - fcm_token: {}건, guest_fcm_region: {}건",
                    deletedTokenCount, deletedGuestRegionCount);
        }

        return totalDeleted;
    }
}
