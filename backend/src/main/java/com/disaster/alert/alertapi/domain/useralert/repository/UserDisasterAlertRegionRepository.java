package com.disaster.alert.alertapi.domain.useralert.repository;

import com.disaster.alert.alertapi.domain.useralert.model.UserDisasterAlertRegion;
import com.disaster.alert.alertapi.domain.useralert.model.UserDisasterAlertRegionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserDisasterAlertRegionRepository extends JpaRepository<UserDisasterAlertRegion, UserDisasterAlertRegionId> {

    /**
     * 특정 UserDisasterAlert의 모든 지역 정보 삭제
     */
    @Modifying
    @Query("DELETE FROM UserDisasterAlertRegion r WHERE r.id.userDisasterAlertId = :alertId")
    void deleteByIdUserDisasterAlertId(@Param("alertId") Long alertId);
}
