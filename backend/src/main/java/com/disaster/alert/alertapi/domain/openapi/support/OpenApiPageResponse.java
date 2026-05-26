package com.disaster.alert.alertapi.domain.openapi.support;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * OpenAPI 공통 페이지 응답 래퍼.
 *
 * <p>Spring의 Page 객체를 직접 노출하지 않고 외부 API 계약에 맞는 형태로 변환한다.
 * 재난문자, 날씨 등 OpenAPI 전체에서 재사용할 수 있도록 제네릭으로 선언했다.
 */
public record OpenApiPageResponse<T>(
        /** 조회된 항목 목록. */
        List<T> items,
        /** 검색 조건에 해당하는 전체 항목 수. */
        long totalElements,
        /** 전체 페이지 수. */
        int totalPages,
        /** 현재 페이지 번호 (0부터 시작). */
        int page,
        /** 페이지당 항목 수. */
        int size
) {
    /** Spring Page 객체를 OpenAPI 응답 래퍼로 변환한다. */
    public static <T> OpenApiPageResponse<T> of(Page<T> pageResult) {
        return new OpenApiPageResponse<>(
                pageResult.getContent(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.getNumber(),
                pageResult.getSize()
        );
    }
}
