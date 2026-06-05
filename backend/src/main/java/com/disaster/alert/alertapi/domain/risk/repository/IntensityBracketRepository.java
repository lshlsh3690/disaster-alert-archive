package com.disaster.alert.alertapi.domain.risk.repository;

import com.disaster.alert.alertapi.domain.risk.model.IntensityBracket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IntensityBracketRepository extends JpaRepository<IntensityBracket, Long> {

    /**
     * 유형 + 강도값이 들어가는 구간의 multiplier 조회.
     * 구간은 [min_value, max_value). 매핑 없으면 호출 측에서 1.0 default.
     */
    @Query("""
            SELECT b.multiplier FROM IntensityBracket b
            WHERE b.disasterType = :type
              AND :value >= b.minValue
              AND :value < b.maxValue
            """)
    Optional<Double> findMultiplier(@Param("type") String type, @Param("value") double value);
}
