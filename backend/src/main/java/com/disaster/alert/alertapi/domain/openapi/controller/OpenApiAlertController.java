package com.disaster.alert.alertapi.domain.openapi.controller;

import com.disaster.alert.alertapi.domain.openapi.dto.OpenApiAlertResponse;
import com.disaster.alert.alertapi.domain.openapi.service.OpenApiAlertService;
import com.disaster.alert.alertapi.domain.openapi.support.OpenApiPageRequest;
import com.disaster.alert.alertapi.domain.openapi.support.OpenApiPageResponse;
import com.disaster.alert.alertapi.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * OpenAPI 재난문자 조회 컨트롤러.
 *
 * <p>일반 사용자 화면용 API와 달리 외부 클라이언트가 서비스키로 호출하는 공개 데이터 API이다.
 * 검색 조건은 날짜 범위만 허용해 덤프형 조회에 집중하고, 내부 구현과 외부 계약을 분리한다.
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
     * <p>공통 OpenApiPageRequest로 페이지 크기를 제한하고,
     * OpenApiPageResponse로 감싸 Spring Page 내부 구현을 외부에 노출하지 않는다.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<OpenApiPageResponse<OpenApiAlertResponse>>> search(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "ko") String lang,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + OpenApiPageRequest.DEFAULT_PAGE_SIZE) int size
    ) {
        Pageable pageable = OpenApiPageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.success(
                OpenApiPageResponse.of(openApiAlertService.search(startDate, endDate, pageable, lang))
        ));
    }
}
