package com.disaster.alert.alertapi.domain.risk.repository;

import com.disaster.alert.alertapi.domain.risk.model.DisasterRiskProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DisasterRiskProfileRepository extends JpaRepository<DisasterRiskProfile, String> {

    /** 유형의 half-life 만 가볍게 조회 (감쇠 계산용). 없으면 호출 측에서 24h default. */
    @Query("SELECT p.halfLifeHours FROM DisasterRiskProfile p WHERE p.disasterType = :type")
    Optional<Integer> findHalfLife(@Param("type") String type);
}
