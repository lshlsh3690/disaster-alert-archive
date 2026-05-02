package com.disaster.alert.alertapi.domain.disasteralert.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DashboardSummaryResponse {
    private long todayOfficialCount;
    private long todayUserCount;
    private long totalUserCount;
    private long totalCombinedCount;
}


