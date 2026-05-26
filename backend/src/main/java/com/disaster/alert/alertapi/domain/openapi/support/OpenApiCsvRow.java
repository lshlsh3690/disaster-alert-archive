package com.disaster.alert.alertapi.domain.openapi.support;

import java.util.List;

/**
 * OpenAPI CSV 응답의 한 행을 표현하는 계약.
 *
 * <p>OpenAPI로 제공되는 리소스가 늘어나도 공통 CSV writer를 재사용하기 위해
 * 각 리소스는 header와 value 목록만 제공하도록 분리했다.
 */
public interface OpenApiCsvRow {
    /** CSV 첫 줄에 사용할 컬럼명 목록. 같은 리소스의 모든 row는 동일한 header를 반환해야 한다. */
    List<String> headers();

    /** CSV 한 줄에 기록할 값 목록. headers와 같은 순서를 유지해야 한다. */
    List<String> values();
}
