package com.disaster.alert.alertapi.domain.weather.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 백필 진행 상황 응답 DTO.
 */
public record BackfillStatusResponse(
        long runningCells,
        long doneCells,
        long failedCells,
        long pendingCells,
        long totalRowsSaved,
        boolean isRunning,
        List<FailedCellSample> failedSamples
) {
    public record FailedCellSample(
            String yearMonth,
            String stnId,
            String errorMsg,
            LocalDateTime finishedAt
    ) {}
}
