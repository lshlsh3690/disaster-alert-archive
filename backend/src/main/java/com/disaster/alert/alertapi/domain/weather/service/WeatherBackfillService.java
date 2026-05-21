package com.disaster.alert.alertapi.domain.weather.service;

import com.disaster.alert.alertapi.domain.weather.api.AsosApiRateLimitException;
import com.disaster.alert.alertapi.domain.weather.model.BackfillStatus;
import com.disaster.alert.alertapi.domain.weather.model.WeatherStationMapping;
import com.disaster.alert.alertapi.domain.weather.repository.WeatherBackfillProgressRepository;
import com.disaster.alert.alertapi.domain.weather.repository.WeatherStationMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ASOS 시간자료 백필 오케스트레이터.
 *
 * <p><b>경계</b>: 백필 endDt 는 실시간 cron 첫 적재 직전 (기본 2026-05-21 11:00 KST).
 * 그 시점 이후는 {@code KMA_NOWCAST} 가 적재하므로 UNIQUE 충돌 자체가 발생하지 않게 분리.
 *
 * <p><b>재개 정책</b>: 셀(month × stnId) 단위로 progress 테이블에 기록.
 * 트리거가 다시 호출되면 DONE 이 아닌 셀만 다시 시도. API 한도/인증 차단 (
 * {@link AsosApiRateLimitException}) 발생 시 즉시 전체 중단.
 *
 * <p><b>Rate limit</b>: 호출 사이 250ms sleep (≈ 4 tps).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WeatherBackfillService {

    /** 호출 사이 sleep — 기상자료개방포털 ASOS API 권고 한계 30 tps 대비 안전한 값. */
    private static final long INTER_CALL_SLEEP_MS = 250L;

    private final WeatherBackfillCellProcessor cellProcessor;
    private final WeatherStationMappingRepository mappingRepository;
    private final WeatherBackfillProgressRepository progressRepository;

    /** 진행 중인 백필이 이미 있는지 — 컨트롤러가 시작 전 확인. */
    public boolean isRunning() {
        return progressRepository.existsByStatus(BackfillStatus.IN_PROGRESS);
    }

    /**
     * 비동기 백필 실행.
     *
     * @param start          시작 월 (포함)
     * @param end            끝 월 (포함). 보통 cutoffEndDate 가 속한 월.
     * @param cutoffEndDate  마지막 월에 대해 day-level cutoff. null 이면 end 월 말일 23시까지.
     * @param cutoffEndHour  마지막 월의 마지막 시각 (KST). cutoffEndDate 와 같이 쓰임.
     */
    @Async("backfillExecutor")
    public void runAsync(YearMonth start, YearMonth end, LocalDate cutoffEndDate, Integer cutoffEndHour) {
        try {
            List<String> stnIds = mappingRepository.findDistinctAsosStationIds();
            // ASOS 지점 → 시군구 매핑 미리 캐싱 (지점 92개 × 평균 2.7시군구 ≈ 250 entry, 부하 무시 가능)
            List<WeatherStationMapping> all = mappingRepository.findAll();
            Map<String, List<WeatherStationMapping>> byStn = all.stream()
                    .filter(m -> m.getAsosStationId() != null)
                    .collect(Collectors.groupingBy(WeatherStationMapping::getAsosStationId));

            log.info("ASOS 백필 시작: {}~{}, 지점={}개, cutoff={} {}시",
                    start, end, stnIds.size(), cutoffEndDate, cutoffEndHour);

            int totalCells = 0;
            int doneCells = 0;
            int failedCells = 0;
            long totalSaved = 0L;

            for (YearMonth ym = start; !ym.isAfter(end); ym = ym.plusMonths(1)) {
                for (String stnId : stnIds) {
                    totalCells++;
                    List<WeatherStationMapping> districts = byStn.getOrDefault(stnId, List.of());
                    if (districts.isEmpty()) {
                        // 정상적으로는 발생하지 않음 (distinct 조회 결과인데도 시군구 매핑이 0개라면 데이터 정합성 문제)
                        log.warn("ASOS 지점 매핑 없음 — skip: stn={}", stnId);
                        continue;
                    }

                    try {
                        int saved = cellProcessor.processCell(ym, stnId, districts, cutoffEndDate, cutoffEndHour);
                        if (saved < 0) {
                            // 이미 DONE 으로 skip
                            doneCells++;
                        } else {
                            doneCells++;
                            totalSaved += saved;
                            log.info("백필 셀 완료: {} stn={} rows={}", ym, stnId, saved);
                        }
                    } catch (AsosApiRateLimitException e) {
                        cellProcessor.markCellFailed(ym, stnId, "RATE_LIMIT: " + e.getMessage());
                        log.warn("ASOS 한도/인증 차단 — 백필 전체 중단: stn={}, msg={}", stnId, e.getMessage());
                        log.info("ASOS 백필 중단 (재개 가능): 총 셀={}, DONE={}, FAILED={}, 적재 row={}",
                                totalCells, doneCells, failedCells + 1, totalSaved);
                        return;
                    } catch (Exception e) {
                        failedCells++;
                        cellProcessor.markCellFailed(ym, stnId, e.getMessage());
                        log.warn("백필 셀 실패 (다음 셀 진행): {} stn={} err={}",
                                ym, stnId, e.getMessage());
                    }

                    sleepBetweenCalls();
                }
            }

            log.info("ASOS 백필 종료: 총 셀={}, DONE={}, FAILED={}, 적재 row={}",
                    totalCells, doneCells, failedCells, totalSaved);
        } catch (Exception unexpected) {
            log.error("ASOS 백필 알 수 없는 오류로 중단", unexpected);
        }
    }

    private void sleepBetweenCalls() {
        try {
            Thread.sleep(INTER_CALL_SLEEP_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
