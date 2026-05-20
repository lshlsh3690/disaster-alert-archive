package com.disaster.alert.alertapi.domain.weather.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 기상청 초단기실황 (getUltraSrtNcst) API 응답 DTO.
 *
 * <p><b>응답 구조 (요약)</b>
 * <pre>
 * {
 *   "response": {
 *     "header": {"resultCode": "00", "resultMsg": "NORMAL_SERVICE"},
 *     "body": {
 *       "items": {
 *         "item": [
 *           {"category": "T1H", "obsrValue": "1.5", ...},
 *           {"category": "RN1", "obsrValue": "0", ...},
 *           ...
 *         ]
 *       },
 *       "totalCount": 8
 *     }
 *   }
 * }
 * </pre>
 *
 * <p><b>우리가 추출하는 카테고리</b>: T1H(기온), RN1(강수), WSD(풍속), VEC(풍향), REH(습도)
 *
 * <p>{@link #isSuccess()} 로 정상 응답 여부 판단.
 * 실패 시 {@code header.resultCode} 가 "00" 외의 값.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UltraSrtNcstResponse(@JsonProperty("response") Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Header header, Body body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<Item> item) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String category,    // T1H, RN1, WSD, VEC, REH, PTY, UUU, VVV
            String obsrValue,   // 관측값 (문자열로 옴)
            String baseDate,
            String baseTime,
            Integer nx,
            Integer ny
    ) {}

    public boolean isSuccess() {
        return response != null
                && response.header() != null
                && "00".equals(response.header().resultCode());
    }

    public List<Item> items() {
        if (response == null || response.body() == null || response.body().items() == null) {
            return List.of();
        }
        List<Item> list = response.body().items().item();
        return list == null ? List.of() : list;
    }

    public String resultMsg() {
        return response != null && response.header() != null
                ? response.header().resultMsg()
                : null;
    }
}
