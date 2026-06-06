package com.disaster.alert.alertapi.domain.notification.repository;

import com.disaster.alert.alertapi.domain.notification.model.GuestFcmRegion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GuestFcmRegionRepository extends JpaRepository<GuestFcmRegion, Long> {

    List<GuestFcmRegion> findAllByFcmToken(String fcmToken);

    List<GuestFcmRegion> findAllByLegalDistrictCodeIn(List<String> codes);

    boolean existsByFcmTokenAndLegalDistrictCode(String fcmToken, String legalDistrictCode);

    long countByFcmToken(String fcmToken);

    void deleteByFcmToken(String fcmToken);

    void deleteByFcmTokenAndLegalDistrictCode(String fcmToken, String legalDistrictCode);
}
