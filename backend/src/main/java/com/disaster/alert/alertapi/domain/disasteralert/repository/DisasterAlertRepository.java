package com.disaster.alert.alertapi.domain.disasteralert.repository;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DisasterAlertRepository extends JpaRepository<DisasterAlert, Long> {
    boolean existsBySn(Long sn);

    @Query("SELECT d.sn FROM DisasterAlert d WHERE d.sn IN :snList")
    List<Long> findExistingSn(@Param("snList") List<Long> snList);
}
