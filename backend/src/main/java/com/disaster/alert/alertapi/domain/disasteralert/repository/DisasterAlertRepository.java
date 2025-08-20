package com.disaster.alert.alertapi.domain.disasteralert.repository;

import com.disaster.alert.alertapi.domain.disasteralert.dto.LatestAlertResponse;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DisasterAlertRepository extends JpaRepository<DisasterAlert, Long>, DisasterAlertRepositoryCustom {
    boolean existsBySn(Long sn);

    @Query("SELECT d.sn FROM DisasterAlert d WHERE d.sn IN :snList")
    List<Long> findExistingSn(@Param("snList") List<Long> snList);

    @Query("SELECT d.legalDistrict.name FROM DisasterAlertRegion d WHERE d.disasterAlert.id = :id")
    List<String> legalDistrictNamesByAlertId(Long id);


    @Query("""
              select new com.disaster.alert.alertapi.domain.disasteralert.dto.LatestAlertResponse(
                d.id,
                d.message,
                d.disasterType,
                d.createdAt,
                coalesce(d.originalRegion, min(ld.name))
              )
              from DisasterAlert d
              left join d.disasterAlertRegions r
              left join r.legalDistrict ld
              group by d.id, d.message, d.createdAt, d.disasterType, d.originalRegion
              order by d.createdAt desc
            """)
    List<LatestAlertResponse> latestAlerts(Pageable pageable);
}
