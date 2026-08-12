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
 * <p><b>전파는 {@code REQUIRES_NEW} 다 — 발송 트랜잭션에 참여시키면 안 된다.</b> 기본 전파였다면
 * 정리 실패가 발송 트랜잭션을 rollback-only 로 표시하고, 커밋 시점에
 * {@code UnexpectedRollbackException} 이 터져 토큰 정리 실패 하나로 그 알림의 발송 이력
 * 전체가 되돌아간다. {@code REQUIRES_NEW} 는 이 rollback-only 오염만 막는다.
 *
 * <p><b>{@code cleanUp()} 이 던지는 예외 자체는 이 전파 설정으로 막히지 않는다</b> — 별도
 * 트랜잭션이어도 예외는 그대로 호출부로 전파된다. 이 예외 전파를 실제로 막는 것은
 * 호출부({@code FcmSendService.sendToToken}/{@code sendToTokens})의 {@code try/catch} 다.
 * 거기서 잡지 않으면 정리 실패 예외가 단건 발송의 {@code return false}나 배치 발송의
 * {@code return response} 를 가로막고, 그 위 {@code AlertNotificationService.sendToMember}
 * 의 {@code catch (Exception)} 이 발송 이력 저장(`notificationLogRepository.save`) 앞에서
 * 예외를 삼켜버려 FCM 은 이미 나갔는데 그 회원의 발송 이력만 통째로 안 남는 결과로 이어진다.
 *
 * <p>그 {@code try/catch} 가 잡는 범위는 <b>{@code DataAccessException} 과
 * {@code TransactionException} 두 계층</b>이다. 둘은 부모-자식이 아니라 형제라서 한쪽만
 * 잡으면 다른 쪽이 그대로 빠져나간다 — 특히 아래 커넥션 비용 문단에서 설명하는 풀 고갈은
 * 트랜잭션 생성 단계에서 {@code CannotCreateTransactionException}({@code TransactionException}
 * 계열)으로 나타나므로, {@code DataAccessException} 만 잡으면 <b>가장 위험한 상황에서만
 * 정확히 방어가 뚫린다</b>. 그 밖의 런타임 예외(코드 버그 등)는 일부러 잡지 않는다.
 *
 * <p>{@code REQUIRES_NEW} 의 통상적 위험인 <b>행 잠금 자기 교착</b>(바깥 트랜잭션이 잠근 행을
 * 안쪽이 기다리는 상황)은 여기서는 없다. 발송 경로는 {@code fcm_token}/{@code guest_fcm_region} 을
 * <b>읽기만</b> 하고 수정하지 않아 그 행들에 쓰기 잠금을 걸지 않는다. 이 전제가 깨지면
 * (발송 중 토큰 행을 UPDATE 하게 되면) 교착이 생기므로 그때 이 결정을 다시 봐야 한다.
 *
 * <p>다만 <b>커넥션 비용은 남는다</b> — 행 잠금 문제가 없다고 해서 공짜라는 뜻이 아니다.
 * 바깥 트랜잭션이 쥔 커넥션은 반납되지 않은 채 이 메서드가 풀에서 하나를 더 빌린다.
 * {@code triggerNotification} 은 한정자 없는 {@code @Async} 라 {@code @Primary} 인
 * {@code translationExecutor}(코어 5 / 최대 10)에서 돌고, Hikari 풀(20)은 웹 요청·스케줄러와
 * 공유된다. 평상시 동시 실행 5개면 이 기능이 10개를 쥐는 셈이고, 대기 작업이 100개 쌓여
 * 스레드가 최대 10까지 늘면 20으로 풀 한도에 닿는다. 바깥 트랜잭션이 회원 수만큼 루프를
 * 도는 동안 계속 열려 있다는 점까지 겹치므로, 발송 규모가 커지면 이 지점을 먼저 의심할 것.
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
