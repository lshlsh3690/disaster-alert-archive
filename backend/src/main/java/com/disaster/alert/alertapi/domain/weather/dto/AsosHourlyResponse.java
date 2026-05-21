package com.disaster.alert.alertapi.domain.weather.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 기상청 ASOS 시간자료 (getWthrDataList, AsosHourlyInfoService) API 응답 DTO.
 *
 * <p><b>응답 구조 (요약)</b>
 * <pre>
 * {
 *   "response": {
 *     "header": {"resultCode": "00", "resultMsg": "NORMAL_SERVICE"},
 *     "body": {
 *       "items": {
 *         "item": [
 *           {"stnId": "108", "stnNm": "서울", "tm": "2024-01-01 14:00",
 *            "ta": "1.5", "rn": "0", "ws": "1.2", "wd": "180", "hm": "62", "pa": "1019.4", ...},
 *           ...
 *         ]
 *       },
 *       "totalCount": 720
 *     }
 *   }
 * }
 * </pre>
 *
 * <p><b>주요 필드</b>: stnId, tm(관측시각), ta(기온), rn(강수), ws(풍속), wd(풍향),
 * hm(상대습도), pa(현지기압) / ps(해면기압) — 공공데이터포털 응답은 pa 가 일반적.
 *
 * <p><b>결측 표현</b>: 빈 문자열, 공백, "-" 등 다양. 호출 측에서 파싱 시 null 로 정규화.
 *
 * <p>{@link #isSuccess()} 로 정상 응답 여부 판단. 호출 한도 초과는 보통 resultCode = "22" 또는 "99".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AsosHourlyResponse(@JsonProperty("response") Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Header header, Body body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items, Integer totalCount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<Item> item) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String stnId,    // 지점 번호
            String stnNm,    // 지점 이름
            String tm,       // 관측 일시 ("YYYY-MM-DD HH:mm")
            String ta,       // 기온 (°C)
            String rn,       // 1시간 강수량 (mm)
            String ws,       // 풍속 (m/s)
            String wd,       // 풍향 (deg)
            String hm,       // 상대 습도 (%)
            String pa,       // 현지 기압 (hPa)
            String ps        // 해면 기압 (hPa) — 일부 응답에 존재
    ) {}

    public boolean isSuccess() {
        return response != null
                && response.header() != null
                && "00".equals(response.header().resultCode());
    }

    public String resultCode() {
        return response != null && response.header() != null
                ? response.header().resultCode()
                : null;
    }

    public String resultMsg() {
        return response != null && response.header() != null
                ? response.header().resultMsg()
                : null;
    }

    public List<Item> items() {
        if (response == null || response.body() == null || response.body().items() == null) {
            return List.of();
        }
        List<Item> list = response.body().items().item();
        return list == null ? List.of() : list;
    }
}
