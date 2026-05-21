package com.disaster.alert.alertapi.domain.weather.api;

import com.disaster.alert.alertapi.domain.weather.dto.AsosHourlyResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * 기상청 ASOS 시간자료 (AsosHourlyInfoService.getWthrDataList) API 호출 클라이언트.
 *
 * <p><b>역할</b>: 백필러가 (1지점, 1개월) 단위로 호출. numOfRows=1000 이라 한 페이지로 720 row 수용 가능.
 *
 * <p><b>실패 정책</b> (cron 클라이언트와 다름)
 * <ul>
 *   <li>resultCode "00" — 정상 응답</li>
 *   <li>resultCode "03" — NODATA. 해당 월에 자료 없음. 빈 리스트 반환 (정상 종료)</li>
 *   <li>resultCode "22","99","30","31","32" — 한도 초과 / 인증 미반영.
 *       {@link AsosApiRateLimitException} 으로 throw → 백필러 전체 중단</li>
 *   <li>네트워크/파싱 예외 — RuntimeException 그대로 throw → 백필러는 해당 셀만 FAILED 처리하고 다음 셀 진행</li>
 * </ul>
 *
 * <p><b>주의</b>: 같은 base URL/serviceKey 를 nowcast 클라이언트와 공유.
 * 활용 신청은 두 오퍼레이션 모두 별도 승인 필요하지만 키는 동일.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KmaAsosHourlyApiClient {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String PATH = "/AsosHourlyInfoService/getWthrDataList";

    private static final Set<String> RATE_LIMIT_CODES =
            Set.of("22", "30", "31", "32", "99");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kma.api.base-url}")
    private String baseUrl;

    @Value("${kma.api.service-key}")
    private String serviceKey;

    /**
     * 한 지점의 [startDate startHh:00, endDate endHh:00] 구간 시간자료 호출.
     * 일반적으로 백필러는 1개월 단위로 호출.
     *
     * @throws AsosApiRateLimitException 호출 한도 초과 / 인증 게이트웨이 미반영 등 즉시 중단 시그널
     */
    public AsosHourlyResponse fetch(String stnId, String startDate, int startHh, String endDate, int endHh) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + PATH)
                .queryParam("serviceKey", serviceKey)
                .queryParam("dataType", "JSON")
                .queryParam("dataCd", "ASOS")
                .queryParam("dateCd", "HR")
                .queryParam("stnIds", stnId)
                .queryParam("startDt", startDate)
                .queryParam("startHh", String.format("%02d", startHh))
                .queryParam("endDt", endDate)
                .queryParam("endHh", String.format("%02d", endHh))
                // KMA ASOS API 는 numOfRows=1000 이상을 "1000건 초과 요청"으로 거부 (code=99).
                // 1개월 = 최대 744 row 라 999 로 한 페이지 수용 가능.
                .queryParam("numOfRows", "999")
                .queryParam("pageNo", "1")
                .build(true)
                .toUri();

        String raw;
        try {
            raw = restTemplate.getForObject(uri, String.class);
        } catch (Exception e) {
            // 네트워크 등 호출 실패. 셀 단위 실패로 처리되도록 RuntimeException 그대로 throw.
            throw new RuntimeException("ASOS API HTTP 호출 실패: stn=" + stnId + ", " + e.getMessage(), e);
        }

        if (raw == null || raw.isBlank()) {
            throw new RuntimeException("ASOS API 빈 응답: stn=" + stnId);
        }

        // 응답이 XML(에러 메시지)인 경우 ─ 게이트웨이가 인증 거부 등으로 XML 에러 반환
        if (raw.trim().startsWith("<")) {
            String snippet = raw.length() > 200 ? raw.substring(0, 200) : raw;
            throw new AsosApiRateLimitException("ASOS API XML 에러 응답 (인증/한도 의심): " + snippet);
        }

        AsosHourlyResponse response;
        try {
            response = objectMapper.readValue(raw, AsosHourlyResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("ASOS API 응답 파싱 실패: " + e.getMessage(), e);
        }

        String code = response.resultCode();
        if ("00".equals(code)) {
            return response;
        }
        if ("03".equals(code)) {
            // NODATA — 해당 구간에 자료 없음. 정상 종료, 빈 리스트.
            log.info("ASOS NODATA: stn={}, {}~{}", stnId, startDate, endDate);
            return response;
        }
        if (RATE_LIMIT_CODES.contains(code)) {
            throw new AsosApiRateLimitException(
                    String.format("ASOS API 한도/인증 에러: code=%s, msg=%s, stn=%s",
                            code, response.resultMsg(), stnId));
        }
        // 알 수 없는 에러 코드도 한도 의심으로 간주해 백필러 중단 (운영에서 안전한 쪽)
        throw new AsosApiRateLimitException(
                String.format("ASOS API 알 수 없는 에러: code=%s, msg=%s, stn=%s",
                        code, response.resultMsg(), stnId));
    }

    /** 1개월 전체 호출 편의 메서드. KST 기준 해당 월 1일 00시 ~ 말일 23시. */
    public AsosHourlyResponse fetchMonth(String stnId, YearMonth ym) {
        String startDate = ym.atDay(1).format(YMD);
        String endDate = ym.atEndOfMonth().format(YMD);
        return fetch(stnId, startDate, 0, endDate, 23);
    }

    /** 마지막 달 부분 구간 호출 (cutoff 적용). [ym-01 00:00 ~ ym-endDay endHh:00]. */
    public AsosHourlyResponse fetchPartial(String stnId, YearMonth ym, int endDayOfMonth, int endHh) {
        String startDate = ym.atDay(1).format(YMD);
        String endDate = ym.atDay(endDayOfMonth).format(YMD);
        return fetch(stnId, startDate, 0, endDate, endHh);
    }
}
