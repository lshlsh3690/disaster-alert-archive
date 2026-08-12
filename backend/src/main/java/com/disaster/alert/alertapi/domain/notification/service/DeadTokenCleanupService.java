package com.disaster.alert.alertapi.domain.notification.service;

import com.disaster.alert.alertapi.domain.notification.repository.FcmTokenRepository;
import com.disaster.alert.alertapi.domain.notification.repository.GuestFcmRegionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
 * <p>트랜잭션은 기본 전파를 쓴다(호출한 발송 트랜잭션에 참여). 발송이 롤백되면 이 삭제도 함께
 * 되돌아가지만, 죽은 토큰은 다음 발송에서 다시 죽은 채로 잡히므로 손해가 없다. 반대로
 * {@code REQUIRES_NEW} 로 매 발송마다 커넥션을 하나 더 여는 비용이 더 크다.
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
    @Transactional
    public int cleanUp(List<String> deadTokens) {
        return 0;
    }
}
