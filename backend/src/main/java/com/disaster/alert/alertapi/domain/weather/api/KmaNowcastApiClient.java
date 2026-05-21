package com.disaster.alert.alertapi.domain.weather.api;

import com.disaster.alert.alertapi.domain.weather.dto.UltraSrtNcstResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 기상청 초단기실황 (getUltraSrtNcst) API 호출 클라이언트.
 *
 * <p><b>특징</b>
 * <ul>
 *   <li>매시 정각 데이터를 매시 40분에 발표 → cron 은 매시 45분에 호출</li>
 *   <li>격자(nx, ny) 단위 호출. 한 격자에 여러 시군구 매핑됨</li>
 *   <li>응답에서 우리가 쓰는 카테고리: T1H/RN1/WSD/VEC/REH</li>
 * </ul>
 *
 * <p><b>장애 정책</b>: 단일 격자 실패는 null 반환 → 호출 측에서 스킵하고 다음 격자 진행.
 * 전체 사이클 중단을 막기 위함.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KmaNowcastApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kma.api.base-url}")
    private String baseUrl;

    @Value("${kma.api.service-key}")
    private String serviceKey;

    private static final String PATH = "/VilageFcstInfoService_2.0/getUltraSrtNcst";

    /**
     * 초단기실황 1회 호출.
     *
     * @param baseDate YYYYMMDD (예: "20260519")
     * @param baseTime HHMM   (예: "1400" — 14시 발표)
     * @param nx       격자 X
     * @param ny       격자 Y
     * @return 응답 객체 (실패 시 null)
     */
    public UltraSrtNcstResponse fetchNowcast(String baseDate, String baseTime, int nx, int ny) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + PATH)
                .queryParam("serviceKey", serviceKey)
                .queryParam("dataType", "JSON")
                .queryParam("numOfRows", "100")
                .queryParam("pageNo", "1")
                .queryParam("base_date", baseDate)
                .queryParam("base_time", baseTime)
                .queryParam("nx", nx)
                .queryParam("ny", ny)
                .build(true)   // serviceKey 가 이미 인코딩된 상태로 들어오므로 한번 더 인코딩 X
                .toUri();

        try {
            String raw = restTemplate.getForObject(uri, String.class);
            if (raw == null || raw.isBlank()) {
                log.warn("KMA nowcast 빈 응답: nx={}, ny={}, base={} {}", nx, ny, baseDate, baseTime);
                return null;
            }
            UltraSrtNcstResponse response = objectMapper.readValue(raw, UltraSrtNcstResponse.class);
            if (!response.isSuccess()) {
                log.warn("KMA nowcast 비정상 응답: nx={}, ny={}, msg={}",
                        nx, ny, response.resultMsg());
                return null;
            }
            return response;
        } catch (Exception e) {
            // 단일 격자 실패는 전체 사이클을 막지 않음
            log.warn("KMA nowcast 호출 실패: nx={}, ny={}, base={} {}, err={}",
                    nx, ny, baseDate, baseTime, e.getMessage());
            return null;
        }
    }
}
