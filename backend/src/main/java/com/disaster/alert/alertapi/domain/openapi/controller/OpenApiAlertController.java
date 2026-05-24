package com.disaster.alert.alertapi.domain.openapi.controller;

import com.disaster.alert.alertapi.domain.disasteralert.dto.AlertSearchRequest;
import com.disaster.alert.alertapi.domain.disasteralert.dto.DisasterAlertResponseDto;
import com.disaster.alert.alertapi.domain.openapi.service.OpenApiAlertService;
import com.disaster.alert.alertapi.domain.openapi.support.OpenApiPageRequest;
import com.disaster.alert.alertapi.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OpenAPI 재난문자 조회 컨트롤러.
 *
 * <p>일반 사용자 화면용 API와 달리 외부 클라이언트가 서비스키로 호출하는 공개 데이터 API이다.
 * 검색 조건은 기존 AlertSearchRequest를 재사용해 내부 검색 정책과 외부 API 검색 계약이 어긋나지 않게 했다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/open-api/alerts")
public class OpenApiAlertController {
    /** OpenAPI 재난문자 조회 유스케이스를 담당하는 서비스. */
    private final OpenApiAlertService openApiAlertService;

    /**
     * 재난문자 데이터를 검색 조건에 따라 조회한다.
     *
     * <p>공통 OpenApiPageRequest를 사용해 OpenAPI 전체에 같은 페이지 제한을 적용한다.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<DisasterAlertResponseDto>>> search(
            AlertSearchRequest searchCondition,
            @RequestParam(defaultValue = "ko") String lang,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + OpenApiPageRequest.DEFAULT_PAGE_SIZE) int size
    ) {
        Pageable pageable = OpenApiPageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DisasterAlertResponseDto> result = openApiAlertService.search(searchCondition, pageable, lang);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
