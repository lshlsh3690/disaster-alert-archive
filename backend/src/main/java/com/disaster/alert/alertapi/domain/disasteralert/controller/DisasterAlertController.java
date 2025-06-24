package com.disaster.alert.alertapi.domain.disasteralert.controller;

import com.disaster.alert.alertapi.domain.disasteralert.dto.DisasterAlertResponseDto;
import com.disaster.alert.alertapi.domain.disasteralert.dto.DisasterAlertStatResponse;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.disaster.alert.alertapi.domain.disasteralert.service.DisasterAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/disaster-alerts")
@RequiredArgsConstructor
public class DisasterAlertController {

    private final DisasterAlertService disasterAlertService;

    /**
     * 전체 재난문자 조회
     */
//    @GetMapping
//    public ResponseEntity<List<DisasterAlertResponseDto>> getAllDisasterAlerts() {
//        List<DisasterAlertResponseDto> alerts = disasterAlertService.findAllAsDto();
//        return ResponseEntity.ok(alerts);
//    }


    @GetMapping("/search")
    public ResponseEntity<Page<DisasterAlertResponseDto>> searchAlerts(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String districtCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) DisasterLevel level,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<DisasterAlertResponseDto> result = disasterAlertService.searchAlerts(region, districtCode, startDate, endDate, type, level, keyword, pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/disasters/stats")
    public ResponseEntity<DisasterAlertStatResponse> getStats(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String districtCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) DisasterLevel level,
            @RequestParam(required = false) String keyword
    ) {
        DisasterAlertStatResponse stats = disasterAlertService.getStats(region, districtCode, startDate, endDate, type, level, keyword);
        return ResponseEntity.ok(stats);
    }

    // 추후 지역, 날짜, 유형 필터를 위한 파라미터 예시:
    // @GetMapping("/search")
    // public ResponseEntity<List<DisasterAlert>> searchAlerts(@RequestParam String region, ...)
}
