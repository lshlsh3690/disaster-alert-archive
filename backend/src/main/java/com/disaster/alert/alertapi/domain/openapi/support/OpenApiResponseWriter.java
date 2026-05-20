package com.disaster.alert.alertapi.domain.openapi.support;

import com.disaster.alert.alertapi.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * OpenAPI 응답 포맷 작성기.
 *
 * <p>OpenAPI 리소스가 늘어나면 json/csv 분기, CSV 헤더, 파일명 생성이 반복될 수 있다.
 * 컨트롤러가 응답 포맷 선택 로직을 직접 갖지 않도록 공통 컴포넌트로 분리했다.
 */
@Component
@RequiredArgsConstructor
public class OpenApiResponseWriter {
    private static final String CSV_FORMAT = "csv";

    /** CSV 문자열 생성을 담당하는 공통 writer. */
    private final OpenApiCsvWriter csvWriter;

    /**
     * 요청 format에 따라 JSON ApiResponse 또는 CSV 파일 응답을 생성한다.
     *
     * <p>JSON은 프로젝트 공통 응답 형식인 ApiResponse로 감싸고,
     * CSV는 파일 다운로드이므로 raw CSV body로 반환한다.
     */
    public <T> ResponseEntity<?> write(String format, String fileNamePrefix, T jsonBody, List<? extends OpenApiCsvRow> csvRows) {
        if (CSV_FORMAT.equalsIgnoreCase(format)) {
            return csv(fileNamePrefix, csvRows);
        }

        return ResponseEntity.ok(ApiResponse.success(jsonBody));
    }

    /** CSV 다운로드 응답을 생성한다. */
    private ResponseEntity<String> csv(String fileNamePrefix, List<? extends OpenApiCsvRow> rows) {
        String body = OpenApiCsvWriter.UTF_8_BOM + csvWriter.write(rows);
        String filename = fileNamePrefix + "-" + LocalDate.now() + ".csv";

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(body);
    }
}
