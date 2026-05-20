package com.disaster.alert.alertapi.domain.openapi.support;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * OpenAPI 공통 페이징 정책.
 *
 * <p>외부 호출자가 size를 과도하게 키워 서버 메모리와 DB 부하를 증가시키지 않도록
 * OpenAPI 전체에서 동일한 기본값과 최대값을 적용한다.
 */
public class OpenApiPageRequest {
    /** size를 생략했을 때 사용하는 기본 조회 건수. */
    public static final int DEFAULT_PAGE_SIZE = 100;

    /** 한 요청에서 허용하는 최대 조회 건수. */
    public static final int MAX_PAGE_SIZE = 1000;

    private OpenApiPageRequest() {
    }

    /** 외부 입력 page/size를 안전한 범위로 보정해 Pageable을 생성한다. */
    public static Pageable of(int page, int size, Sort sort) {
        return PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                sort
        );
    }
}
