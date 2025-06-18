package com.disaster.alert.alertapi.domain.disasteralert.repository;

import com.disaster.alert.alertapi.domain.disasteralert.dto.AlertSearchCondition;
import com.disaster.alert.alertapi.domain.disasteralert.dto.DisasterAlertRegionStatDto;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface DisasterAlertRepositoryCustom {
    Page<DisasterAlert> searchAlerts(AlertSearchCondition condition, Pageable pageable);

    List<DisasterAlert> getRegionStats(AlertSearchCondition alertSearchCondition);
}
