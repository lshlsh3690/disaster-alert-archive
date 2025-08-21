package com.disaster.alert.alertapi.domain.disasteralert.controller;

import com.disaster.alert.alertapi.domain.disasteralert.dto.*;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.disaster.alert.alertapi.domain.disasteralert.service.DisasterAlertService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Slf4j
public class DisasterAlertController {

    private final DisasterAlertService disasterAlertService;

    /**
     * 재난 경고 조회 API
     * @param alertSearchRequest 검색 조건
     * @param pageable     페이지 정보
     * @return 재난 안전문자 목록
     */
    @GetMapping("/search")
    public ResponseEntity<Page<DisasterAlertResponseDto>> searchAlerts(
            AlertSearchRequest alertSearchRequest, Pageable pageable
    ) {
        log.info("Searching alerts with condition: {}", alertSearchRequest);
        Page<DisasterAlertResponseDto> result = disasterAlertService.searchAlerts(alertSearchRequest, pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * 재난 안전문자 통계 조회 API
     *
     * @param alertSearchRequest 검색 조건
     * @return 재난 안전문자 통계 정보
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

    /**
     * 재난 안전문자 상세 조회 API
     *
     * @param id 재난 경고 ID
     * @return 재난 안전문자 상세 정보
     */
    @GetMapping("/{id}")
    public ResponseEntity<DisasterAlertDetailDto> getDisasterAlert(@PathVariable Long id) {
        DisasterAlertDetailDto dto = disasterAlertService.getAlertDetail(id);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/latest")
    public ResponseEntity<List<LatestAlertResponse>> getLatestAlert(
            @RequestParam(defaultValue = "5") @Min(1) @Max(50) int limit
    ) {
       return ResponseEntity.ok(disasterAlertService.getLatestAlert(limit));
    }
}
