package com.disaster.alert.alertapi.domain.weather.controller;

import com.disaster.alert.alertapi.domain.weather.dto.BackfillStatusResponse;
import com.disaster.alert.alertapi.domain.weather.service.WeatherBackfillService;
import com.disaster.alert.alertapi.domain.weather.service.WeatherBackfillStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

/**
 * 운영자용 ASOS 시간자료 백필 트리거 컨트롤러.
 *
 * <p><b>전형적 운영 흐름</b>
 * <ol>
 *   <li>POST /api/v1/admin/weather/backfill — 비동기 시작, 즉시 202</li>
 *   <li>GET .../status 로 모니터링</li>
 *   <li>API 한도 등으로 도중 중단되면 다시 POST → 미완료 셀부터 자동 재개</li>
 * </ol>
 *
 * <p><b>기본 cutoff</b>: 실시간 cron 첫 적재 직전 (2026-05-21 11:00 KST).
 * cron 머지 시각 2026-05-21 03:00 UTC = 12:00 KST 가 첫 base_time 이라 그 직전까지 백필.
 */
@RestController
@RequestMapping("/api/v1/admin/weather/backfill")
@RequiredArgsConstructor
@Slf4j
public class WeatherBackfillAdminController {

    /** 백필 시작 월 기본값 — 재난 데이터가 누적되기 시작한 시점. */
    private static final YearMonth DEFAULT_START_YM = YearMonth.of(2023, 9);
    private static final YearMonth DEFAULT_END_YM = YearMonth.of(2026, 5);
    private static final LocalDate DEFAULT_CUTOFF_DATE = LocalDate.of(2026, 5, 21);
    private static final int DEFAULT_CUTOFF_HOUR = 11;

    private final WeatherBackfillService backfillService;
    private final WeatherBackfillStatusService statusService;

    /**
     * 비동기 백필 시작. 동시 실행 방지를 위해 IN_PROGRESS 셀이 존재하면 409.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> startBackfill(
            @RequestParam(required = false) String startYm,
            @RequestParam(required = false) String endYm,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer endHour
    ) {
        if (backfillService.isRunning()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "backfill already in progress"));
        }

        YearMonth start = startYm != null ? YearMonth.parse(startYm) : DEFAULT_START_YM;
        YearMonth end = endYm != null ? YearMonth.parse(endYm) : DEFAULT_END_YM;
        LocalDate cutoffDate = endDate != null ? LocalDate.parse(endDate) : DEFAULT_CUTOFF_DATE;
        int cutoffHour = endHour != null ? endHour : DEFAULT_CUTOFF_HOUR;

        if (start.isAfter(end)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "startYm must be <= endYm"));
        }

        log.info("백필 트리거: {}~{}, cutoff={} {}시", start, end, cutoffDate, cutoffHour);
        backfillService.runAsync(start, end, cutoffDate, cutoffHour);

        return ResponseEntity.accepted().body(Map.of(
                "message", "backfill started",
                "startYm", start.toString(),
                "endYm", end.toString(),
                "cutoffDate", cutoffDate.toString(),
                "cutoffHour", cutoffHour
        ));
    }

    /** 진행 상황 조회. */
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BackfillStatusResponse> status() {
        return ResponseEntity.ok(statusService.fetchStatus());
    }
}
