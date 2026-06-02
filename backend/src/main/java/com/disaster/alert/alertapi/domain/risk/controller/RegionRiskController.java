package com.disaster.alert.alertapi.domain.risk.controller;

import com.disaster.alert.alertapi.global.dto.ApiResponse;
import com.disaster.alert.alertapi.domain.risk.controller.dto.RiskResponses.*;
import com.disaster.alert.alertapi.domain.risk.service.RegionRiskQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 지역 위험도 조회 API. FE 차트/히트맵(D5)·트렌드용.
 *
 * <p>조회 오케스트레이션은 {@link RegionRiskQueryService} 에 위임 — 컨트롤러는 라우팅만.
 */
@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionRiskController {

    private final RegionRiskQueryService queryService;

    /** 전국 시군구 위험도 (히트맵). */
    @GetMapping("/risk-map")
    public ResponseEntity<ApiResponse<List<RegionRiskMapItem>>> riskMap() {
        return ResponseEntity.ok(ApiResponse.success(queryService.riskMap()));
    }

    /** 특정 시군구 상세 + 기여 이벤트들. */
    @GetMapping("/{regionCode}/risk")
    public ResponseEntity<ApiResponse<RegionRiskDetail>> regionRisk(@PathVariable String regionCode) {
        return ResponseEntity.ok(ApiResponse.success(queryService.regionRisk(regionCode)));
    }

    /** 시군구 위험도 시계열 (트렌드 / Phase 2 Chronos UI). */
    @GetMapping("/{regionCode}/risk/history")
    public ResponseEntity<ApiResponse<List<RiskHistoryPoint>>> history(@PathVariable String regionCode,
                                                                       @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(ApiResponse.success(queryService.history(regionCode, days)));
    }
}
