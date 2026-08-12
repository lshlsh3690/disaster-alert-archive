package com.disaster.alert.alertapi.domain.notification.repository;

import com.disaster.alert.alertapi.domain.notification.model.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    Optional<FcmToken> findByMemberIdAndDeviceType(Long memberId, String deviceType);

    List<FcmToken> findAllByMemberId(Long memberId);

    Optional<FcmToken> findByToken(String token);

    // FCM 이 영구 무효로 판정한 토큰들을 한 번에 삭제한다. 파생 delete 는 엔티티를 먼저 조회해
    // 건별로 지우지만, 여기서는 지울 대상이 이미 확정돼 있어 단일 DML 이면 충분하다.
    // 삭제된 행 수를 돌려주므로 호출부가 "실제로 몇 개를 정리했는지" 로그로 남길 수 있다.
    //
    // 호출부가 지켜야 할 것 두 가지:
    // 1) 빈 리스트로 호출하지 말 것. 가드는 DeadTokenCleanupService.cleanUp 에 있고 여기엔 없다.
    // 2) 같은 트랜잭션에서 삭제 대상 FcmToken 을 managed 상태로 붙들고 있지 말 것. bulk delete 는
    //    영속성 컨텍스트를 갱신하지 않아 지워진 행의 엔티티가 1차 캐시에 남고, 이후 더티 체킹이
    //    그 행에 UPDATE 를 시도하면 깨진다. clearAutomatically 를 쓰지 않은 것은 의도적이다 —
    //    그 옵션은 삭제된 엔티티만이 아니라 호출자의 영속성 컨텍스트 '전체'를 비워서, 발송 루프
    //    한가운데서 리포지토리가 호출자의 상태를 날리는 부작용이 생긴다.
    @Modifying
    @Query("delete from FcmToken f where f.token in :tokens")
    int deleteAllByTokenIn(@Param("tokens") List<String> tokens);
}