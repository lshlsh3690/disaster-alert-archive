package com.disaster.alert.alertapi.domain.weather.service;

import com.disaster.alert.alertapi.domain.weather.api.KmaNowcastApiClient;
import com.disaster.alert.alertapi.domain.weather.dto.UltraSrtNcstResponse;
import com.disaster.alert.alertapi.domain.weather.model.WeatherSource;
import com.disaster.alert.alertapi.domain.weather.model.WeatherStationMapping;
import com.disaster.alert.alertapi.domain.weather.repository.WeatherObservationRepository;
import com.disaster.alert.alertapi.domain.weather.repository.WeatherStationMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 매시간 초단기실황 수집 서비스.
 *
 * <p><b>흐름</b>
 * <ol>
 *   <li>weather_station_mapping 에서 (nx, ny) 별로 시군구 그룹화 — 한 격자 호출이 N 시군구 커버</li>
 *   <li>각 격자 별 KMA API 호출 → 응답 파싱 (T1H/RN1/WSD/VEC/REH)</li>
 *   <li>해당 격자에 매핑된 모든 시군구로 upsert 적재 (source=KMA_NOWCAST)</li>
 * </ol>
 *
 * <p><b>실패 정책</b>
 * <ul>
 *   <li>단일 격자 API 실패 → 로그 남기고 다음 격자로 진행 (전체 사이클 보호)</li>
 *   <li>전체 사이클 중간 예외 → 외부 try/catch (스케줄러 측) 가 잡음</li>
 * </ul>
 *
 * <p><b>호출 시점</b>: 매시 45분 cron 권장 (정각 40분 발표 + 5분 여유).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WeatherCollectService {

    private static final DateTimeFormatter BASE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final KmaNowcastApiClient apiClient;
    private final WeatherStationMappingRepository mappingRepository;
    private final WeatherObservationRepository observationRepository;

    /**
     * 1회 수집 실행. 스케줄러/컨트롤러에서 호출.
     *
     * @return 적재한 시군구 row 수
     */
    @Transactional
    public int collectAndSave() {
        // 1. (nx, ny) 별 시군구 그룹화
        List<WeatherStationMapping> mappings = mappingRepository.findAll();
        Map<GridKey, List<WeatherStationMapping>> byGrid = new HashMap<>();
        for (WeatherStationMapping m : mappings) {
            if (m.getKmaNx() == null || m.getKmaNy() == null) {
                continue;
            }
            byGrid.computeIfAbsent(new GridKey(m.getKmaNx(), m.getKmaNy()), k -> new java.util.ArrayList<>())
                    .add(m);
        }

        // 2. 기준 시각 — 직전 정각
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime observedAt = now.withMinute(0).withSecond(0).withNano(0);
        String baseDate = observedAt.format(BASE_DATE_FMT);
        String baseTime = String.format("%02d00", observedAt.getHour());

        log.info("KMA nowcast 수집 시작: base={} {}, 격자={}개, 시군구={}개",
                baseDate, baseTime, byGrid.size(), mappings.size());

        int totalSaved = 0;
        int failedGrids = 0;

        // 3. 격자별 호출 → 시군구별 적재
        for (Map.Entry<GridKey, List<WeatherStationMapping>> entry : byGrid.entrySet()) {
            GridKey grid = entry.getKey();
            List<WeatherStationMapping> districts = entry.getValue();

            UltraSrtNcstResponse response = apiClient.fetchNowcast(baseDate, baseTime, grid.nx, grid.ny);
            if (response == null) {
                failedGrids++;
                continue;   // 단일 격자 실패는 다음 격자로
            }

            WeatherValues values = parseValues(response);

            for (WeatherStationMapping m : districts) {
                try {
                    observationRepository.upsert(
                            m.getLegalDistrictCode(),
                            observedAt,
                            values.temperature, values.precipitation,
                            values.windSpeed, values.windDirection,
                            values.humidity, null,   // 초단기실황은 기압 없음
                            WeatherSource.KMA_NOWCAST.name()
                    );
                    totalSaved++;
                } catch (Exception e) {
                    log.warn("upsert 실패: code={}, observedAt={}, err={}",
                            m.getLegalDistrictCode(), observedAt, e.getMessage());
                }
            }
        }

        log.info("KMA nowcast 수집 종료: 적재={}, 실패격자={}/{}", totalSaved, failedGrids, byGrid.size());
        return totalSaved;
    }

    /**
     * 응답의 카테고리별 값을 우리 컬럼으로 매핑.
     * 결측/이상값은 null 로.
     */
    private WeatherValues parseValues(UltraSrtNcstResponse response) {
        WeatherValues v = new WeatherValues();
        for (UltraSrtNcstResponse.Item item : response.items()) {
            String cat = item.category();
            String raw = item.obsrValue();
            if (cat == null || raw == null || raw.isBlank()) continue;

            switch (cat) {
                case "T1H" -> v.temperature   = parseDoubleOrNull(raw);
                case "RN1" -> v.precipitation = parsePrecipitation(raw);
                case "WSD" -> v.windSpeed     = parseDoubleOrNull(raw);
                case "VEC" -> v.windDirection = parseIntOrNull(raw);
                case "REH" -> v.humidity      = parseDoubleOrNull(raw);
                // PTY, UUU, VVV 는 사용 안 함
                default -> { /* ignore */ }
            }
        }
        return v;
    }

    /** 기상청 강수량은 "강수없음", "30.0~50.0mm" 같은 문자열도 옴. 숫자만 추출. */
    private Double parsePrecipitation(String raw) {
        String s = raw.trim();
        if (s.isEmpty() || s.equals("강수없음") || s.equals("0")) return 0.0;
        // 범위 형태 ("30.0~50.0mm") → 하한값만 사용
        if (s.contains("~")) {
            s = s.substring(0, s.indexOf("~")).trim();
        }
        // 단위 제거
        s = s.replace("mm", "").trim();
        return parseDoubleOrNull(s);
    }

    private Double parseDoubleOrNull(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseIntOrNull(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            try {
                // "180.0" 같은 형식 대응
                return (int) Double.parseDouble(s.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    /**
     * 격자 그룹화 key.
     */
    private record GridKey(int nx, int ny) {}

    /**
     * 한 격자에서 파싱한 우리 모델 변수들.
     * 단순 mutable 컨테이너 — 메서드 안에서만 사용.
     */
    private static class WeatherValues {
        Double temperature;
        Double precipitation;
        Double windSpeed;
        Integer windDirection;
        Double humidity;
    }
}
