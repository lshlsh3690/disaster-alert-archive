package com.disaster.alert.alertapi.domain.disasteralert.controller;

import com.disaster.alert.alertapi.domain.disasteralert.dto.*;
import com.disaster.alert.alertapi.domain.disasteralert.service.DisasterAlertService;
import com.disaster.alert.alertapi.domain.useralert.service.UserDisasterAlertService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Slf4j
public class DisasterAlertController {

    private final DisasterAlertService disasterAlertService;
    private final UserDisasterAlertService userDisasterAlertService;

    /**
     * 재난문자 검색 (페이지네이션).
     *
     * @param lang 응답 언어 — "ko"(기본)/"en"/"ja"/"zh".
     *             "ko" 또는 미지정 시 번역 필드는 모두 null.
     *             다른 값은 해당 언어로 번역 (캐시 없으면 DeepL 호출).
     */
    @GetMapping("/search")
    public ResponseEntity<Page<DisasterAlertResponseDto>> searchAlerts(
            AlertSearchRequest alertSearchRequest,
            Pageable pageable,
            @RequestParam(defaultValue = "ko") String lang
    ) {
        log.info("Searching alerts with condition: {}, lang: {}", alertSearchRequest, lang);
        Page<DisasterAlertResponseDto> result = disasterAlertService.searchAlerts(alertSearchRequest, pageable, lang);
        return ResponseEntity.ok(result);
    }

    /**
     * 공식 + 사용자 제보 통합 검색.
     *
     * <p>USER 제보는 번역 시스템이 없으므로 OFFICIAL 항목만 번역됨.
     */
    @GetMapping("/search/combined")
    public ResponseEntity<Page<CombinedAlertResponse>> searchCombined(
            AlertSearchRequest alertSearchRequest,
            @RequestParam(defaultValue = "ALL") String source, // ALL | OFFICIAL | USER
            Pageable pageable,
            @RequestParam(defaultValue = "ko") String lang
    ) {
        return ResponseEntity.ok(disasterAlertService.searchCombined(alertSearchRequest, source, pageable, lang));
    }

    /**
     * 재난문자 통계 조회 (숫자/카운트만 반환하므로 lang 파라미터 없음).
     */
    @GetMapping("/stats")
    public ResponseEntity<DisasterAlertStatResponse> getStats(AlertSearchRequest alertSearchRequest) {
        DisasterAlertStatResponse stats = disasterAlertService.getStats(alertSearchRequest);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/stats/sido")
    public ResponseEntity<List<DisasterAlertStatResponse.RegionStat>> countBySido(
            AlertSearchRequest request
    ) {
        return ResponseEntity.ok(disasterAlertService.countBySido(request));
    }

    @GetMapping("/stats/sigungu")
    public ResponseEntity<List<DisasterAlertStatResponse.RegionStat>> countBySigungu(
            AlertSearchRequest request
    ) {
        return ResponseEntity.ok(disasterAlertService.countBySigungu(request));
    }

    @GetMapping("/stats/daily")
    public ResponseEntity<List<DisasterAlertStatResponse.DailyStat>> countByDate(
            AlertSearchRequest request
    ) {
        return ResponseEntity.ok(disasterAlertService.countByDate(request));
    }

    @GetMapping("/stats/hourly")
    public ResponseEntity<List<DisasterAlertStatResponse.HourlyStat>> countByHour(
            AlertSearchRequest request
    ) {
        return ResponseEntity.ok(disasterAlertService.countByHour(request));
    }

    @GetMapping("/stats/monthly-type")
    public ResponseEntity<List<DisasterAlertStatResponse.MonthlyTypeStat>> countByMonthType(
            AlertSearchRequest request
    ) {
        return ResponseEntity.ok(disasterAlertService.countByMonthType(request));
    }

    /**
     * 재난문자 상세 조회.
     *
     * @param lang 응답 언어. 캐시 없으면 lazy 번역 (DeepL 호출).
     */
    @GetMapping("/{id}")
    public ResponseEntity<DisasterAlertDetailDto> getDisasterAlert(
            @PathVariable Long id,
            @RequestParam(defaultValue = "ko") String lang
    ) {
        DisasterAlertDetailDto dto = disasterAlertService.getAlertDetail(id, lang);
        return ResponseEntity.ok(dto);
    }

    /**
     * 최신 재난문자 N건 (대시보드/홈 화면).
     */
    @GetMapping("/latest")
    public ResponseEntity<List<LatestAlertResponse>> getLatestAlert(
            @RequestParam(defaultValue = "5") @Min(1) @Max(50) int limit,
            @RequestParam(defaultValue = "ko") String lang
    ) {
        return ResponseEntity.ok(disasterAlertService.getLatestAlert(limit, lang));
    }

    @GetMapping("/stats/weather-correlation")
    public ResponseEntity<List<WeatherCorrelationDto>> getWeatherCorrelation(AlertSearchRequest request) {
        return ResponseEntity.ok(disasterAlertService.getWeatherCorrelation(request));
    }

    @GetMapping("/stats/weather-by-type")
    public ResponseEntity<List<WeatherTypeStatDto>> getWeatherByType(AlertSearchRequest request) {
        return ResponseEntity.ok(disasterAlertService.getWeatherByType(request));
    }

    @GetMapping("/stats/weather-by-region")
    public ResponseEntity<List<WeatherRegionStatDto>> getWeatherByRegion(
            AlertSearchRequest request,
            @RequestParam(defaultValue = "sido") String groupBy) {
        List<WeatherRegionStatDto> data = "sigungu".equals(groupBy)
                ? disasterAlertService.getWeatherBySigungu(request)
                : disasterAlertService.getWeatherBySido(request);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/{id}/weather")
    public ResponseEntity<AlertWeatherDto> getAlertWeather(@PathVariable Long id) {
        return disasterAlertService.getAlertWeather(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * 대시보드 요약 통계 (카운트만 반환하므로 lang 파라미터 없음).
     */
    @GetMapping("/dashboard/summary")
    public ResponseEntity<DashboardSummaryResponse> getDashboardSummary() {
        long totalUserCount = userDisasterAlertService.countAllActive();
        long todayUserCount = userDisasterAlertService.countTodayActive();
        return ResponseEntity.ok(disasterAlertService.getDashboardSummary(todayUserCount, totalUserCount));
    }
}
