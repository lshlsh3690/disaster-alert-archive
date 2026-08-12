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
    @Modifying
    @Query("delete from FcmToken f where f.token in :tokens")
    int deleteAllByTokenIn(@Param("tokens") List<String> tokens);
}