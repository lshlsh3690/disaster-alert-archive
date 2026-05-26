package com.disaster.alert.alertapi.domain.openapi.support;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * OpenAPI CSV 문자열 생성기.
 *
 * <p>CSV 이스케이프 규칙은 리소스마다 반복되기 쉬우므로 공통 writer로 분리했다.
 * 각 리소스는 OpenApiCsvRow만 구현하면 동일한 CSV 생성 규칙을 사용할 수 있다.
 */
@Component
public class OpenApiCsvWriter {
    /** Excel에서 UTF-8 CSV를 안정적으로 열 수 있도록 붙이는 BOM. */
    public static final String UTF_8_BOM = "\uFEFF";

    /** CSV row 목록을 CSV 문자열로 변환한다. row가 없으면 header 없이 빈 CSV를 반환한다. */
    public String write(List<? extends OpenApiCsvRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }

        StringBuilder csv = new StringBuilder();
        csv.append(toLine(rows.get(0).headers()));

        for (OpenApiCsvRow row : rows) {
            csv.append(toLine(row.values()));
        }

        return csv.toString();
    }

    /** 값 목록을 CSV 한 줄로 변환한다. */
    private String toLine(List<String> values) {
        return values.stream()
                .map(this::escape)
                .collect(Collectors.joining(",")) + "\n";
    }

    /** CSV 규칙에 맞게 쉼표, 따옴표, 줄바꿈이 포함된 값을 이스케이프한다. */
    private String escape(String value) {
        if (value == null) {
            return "";
        }

        String text = value
                .replace("\r\n", "\n")
                .replace("\r", "\n");

        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }

        return text;
    }
}
