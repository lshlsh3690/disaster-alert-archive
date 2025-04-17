package com.disaster.alert.alertapi.domain.disasteralert.repository;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisasterAlertRepository extends JpaRepository<DisasterAlert, Long> {
    boolean existsBySn(Long sn);
}
