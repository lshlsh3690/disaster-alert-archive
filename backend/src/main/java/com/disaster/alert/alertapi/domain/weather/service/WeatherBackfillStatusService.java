package com.disaster.alert.alertapi.domain.weather.service;

import com.disaster.alert.alertapi.domain.weather.dto.BackfillStatusResponse;
import com.disaster.alert.alertapi.domain.weather.model.BackfillStatus;
import com.disaster.alert.alertapi.domain.weather.repository.WeatherBackfillProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 백필 진행 상태 조회용 read-only 서비스.
 * 백필 실행 로직과 분리 — 상태 조회는 실행 중에도 가벼운 read.
 */
@Service
@RequiredArgsConstructor
public class WeatherBackfillStatusService {

    private final WeatherBackfillProgressRepository progressRepository;

    @Transactional(readOnly = true)
    public BackfillStatusResponse fetchStatus() {
        long running = progressRepository.countByStatus(BackfillStatus.IN_PROGRESS);
        long done = progressRepository.countByStatus(BackfillStatus.DONE);
        long failed = progressRepository.countByStatus(BackfillStatus.FAILED);
        long pending = progressRepository.countByStatus(BackfillStatus.PENDING);
        long totalSaved = progressRepository.sumRowCountOfDone();

        List<BackfillStatusResponse.FailedCellSample> samples =
                progressRepository.findTop20ByStatusOrderByFinishedAtDesc(BackfillStatus.FAILED)
                        .stream()
                        .map(p -> new BackfillStatusResponse.FailedCellSample(
                                p.getId().getYearMonth(),
                                p.getId().getStnId(),
                                p.getErrorMsg(),
                                p.getFinishedAt()))
                        .toList();

        return new BackfillStatusResponse(
                running, done, failed, pending, totalSaved, running > 0, samples);
    }
}
