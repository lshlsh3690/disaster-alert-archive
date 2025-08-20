package com.disaster.alert.alertapi.domain.disasteralert.repository;

import com.disaster.alert.alertapi.domain.disasteralert.dto.AlertSearchRequest;
import com.disaster.alert.alertapi.domain.disasteralert.dto.DisasterAlertStatResponse;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface DisasterAlertRepositoryCustom {
    Page<DisasterAlert> searchAlerts(AlertSearchRequest alertSearchRequest, Pageable pageable);

    long countAlerts(AlertSearchRequest request);

    List<DisasterAlertStatResponse.RegionStat> countByRegion(AlertSearchRequest request);

    List<DisasterAlertStatResponse.TypeStat> countByType(AlertSearchRequest request);

    List<DisasterAlertStatResponse.LevelStat> countByLevel(AlertSearchRequest request);
}
