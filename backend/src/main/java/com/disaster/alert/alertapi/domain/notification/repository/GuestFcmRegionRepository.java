package com.disaster.alert.alertapi.domain.notification.repository;

import com.disaster.alert.alertapi.domain.notification.model.GuestFcmRegion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GuestFcmRegionRepository extends JpaRepository<GuestFcmRegion, Long> {

    List<GuestFcmRegion> findAllByFcmToken(String fcmToken);

    List<GuestFcmRegion> findAllByLegalDistrictCodeIn(List<String> codes);

    boolean existsByFcmTokenAndLegalDistrictCode(String fcmToken, String legalDistrictCode);

    long countByFcmToken(String fcmToken);

    // 즉시 실행되는 bulk delete. 파생 delete 는 flush 시점까지 지연되는데, GuestFcmRegion 이
    // IDENTITY 전략이라 뒤따르는 save()가 즉시 INSERT 되면서 "삭제 전 재삽입"으로 UNIQUE
    // (fcm_token, legal_district_code) 충돌이 났다(같은 토큰 지역 재등록 시). bulk delete 로
    // 삭제를 먼저 확정해 충돌을 없앤다.
    @Modifying
    @Query("delete from GuestFcmRegion g where g.fcmToken = :fcmToken")
    void deleteByFcmToken(@Param("fcmToken") String fcmToken);

    void deleteByFcmTokenAndLegalDistrictCode(String fcmToken, String legalDistrictCode);
}
