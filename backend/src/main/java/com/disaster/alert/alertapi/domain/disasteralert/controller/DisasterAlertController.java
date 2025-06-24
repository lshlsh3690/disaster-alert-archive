package com.disaster.alert.alertapi.domain.disasteralert.controller;

import com.disaster.alert.alertapi.domain.disasteralert.dto.DisasterAlertDetailDto;
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
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class DisasterAlertController {

    private final DisasterAlertService disasterAlertService;

    /**
     * 재난 경고 조회 API
     * @param region 지역명
     * @param districtCode 법정동 코드
     * @param startDate 시작 날짜 (ISO 8601 형식)
     * @param endDate 종료 날짜 (ISO 8601 형식)
     * @param type 재난 유형
     * @param level 재난 수준
     * @param keyword 검색 키워드
     * @param pageable 페이지 정보
     * @return 재난 안전문자 목록
     */
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

    /**
     * 재난 안전문자 통계 조회 API
     * @param region 지역명
     * @param districtCode 법정동 코드
     * @param startDate 시작 날짜 (ISO 8601 형식)
     * @param endDate 종료 날짜 (ISO 8601 형식)
     * @param type 재난 유형
     * @param level 재난 수준
     * @param keyword 검색 키워드
     * @return 재난 안전문자 통계 정보
     */
    @GetMapping("/stats")
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


    /**
     * 재난 안전문자 상세 조회 API
     * @param id 재난 경고 ID
     * @return 재난 안전문자 상세 정보
     */
    @GetMapping("/{id}")
    public ResponseEntity<DisasterAlertDetailDto> getDisasterAlert(@PathVariable Long id) {
        DisasterAlertDetailDto dto = disasterAlertService.getAlertDetail(id);
        return ResponseEntity.ok(dto);
    }


    // 추후 지역, 날짜, 유형 필터를 위한 파라미터 예시:
    // @GetMapping("/search")
    // public ResponseEntity<List<DisasterAlert>> searchAlerts(@RequestParam String region, ...)
}
