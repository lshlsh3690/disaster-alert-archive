package com.disaster.alert.alertapi.domain.openapi.controller;

import com.disaster.alert.alertapi.domain.disasteralert.dto.AlertSearchRequest;
import com.disaster.alert.alertapi.domain.disasteralert.dto.DisasterAlertResponseDto;
import com.disaster.alert.alertapi.domain.openapi.dto.OpenApiAlertCsvRow;
import com.disaster.alert.alertapi.domain.openapi.service.OpenApiAlertService;
import com.disaster.alert.alertapi.domain.openapi.support.OpenApiPageRequest;
import com.disaster.alert.alertapi.domain.openapi.support.OpenApiResponseWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/open-api/alerts")
/**
 * OpenAPI 재난문자 조회 컨트롤러.
 *
 * <p>일반 사용자 화면용 API와 달리 외부 클라이언트가 서비스키로 호출하는 공개 데이터 API이다.
 * 검색 조건은 기존 AlertSearchRequest를 재사용해 내부 검색 정책과 외부 API 검색 계약이 어긋나지 않게 했다.
 * 응답 포맷은 JSON과 CSV를 모두 지원하므로 공통 OpenAPI response writer로 응답 생성을 위임한다.
 */
public class OpenApiAlertController {
    /** OpenAPI 재난문자 조회 유스케이스를 담당하는 서비스. */
    private final OpenApiAlertService openApiAlertService;

    /** JSON/CSV 응답 포맷 선택과 CSV 파일 응답 생성을 담당하는 공통 writer. */
    private final OpenApiResponseWriter openApiResponseWriter;

    /**
     * 재난문자 데이터를 검색 조건에 따라 조회한다.
     *
     * <p>format=json이면 ApiResponse로 감싼 Page JSON을 반환하고,
     * format=csv이면 현재 페이지 데이터만 CSV 파일로 반환한다.
     * 공통 OpenApiPageRequest를 사용해 OpenAPI 전체에 같은 페이지 제한을 적용한다.
     */
    @GetMapping
    public ResponseEntity<?> search(
            AlertSearchRequest searchCondition,
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(defaultValue = "ko") String lang,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + OpenApiPageRequest.DEFAULT_PAGE_SIZE) int size
    ) {
        Pageable pageable = OpenApiPageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DisasterAlertResponseDto> result = openApiAlertService.search(searchCondition, pageable, lang);
        var csvRows = result.getContent().stream()
                .map(OpenApiAlertCsvRow::from)
                .collect(Collectors.toList());

        return openApiResponseWriter.write(format, "disaster-alerts", result, csvRows);
    }
}
