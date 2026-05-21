package com.disaster.alert.alertapi.domain.weather.service;

import com.disaster.alert.alertapi.domain.weather.api.KmaAsosHourlyApiClient;
import com.disaster.alert.alertapi.domain.weather.dto.AsosHourlyResponse;
import com.disaster.alert.alertapi.domain.weather.model.BackfillStatus;
import com.disaster.alert.alertapi.domain.weather.model.WeatherBackfillProgress;
import com.disaster.alert.alertapi.domain.weather.model.WeatherBackfillProgressId;
import com.disaster.alert.alertapi.domain.weather.model.WeatherSource;
import com.disaster.alert.alertapi.domain.weather.model.WeatherStationMapping;
import com.disaster.alert.alertapi.domain.weather.repository.WeatherBackfillProgressRepository;
import com.disaster.alert.alertapi.domain.weather.repository.WeatherObservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 백필 한 셀 (1개월 × 1지점) 처리기.
 *
 * <p>{@link WeatherBackfillService} 의 외부 루프가 호출. 한 셀이 한 트랜잭션 단위.
 * 분리한 이유: {@code @Async} 메서드 내부에서 같은 클래스의 {@code @Transactional} 메서드를
 * 호출하면 Spring AOP 가 가로채지 못해 트랜잭션이 적용되지 않음 — 별도 빈으로 분리.
 *
 * <p>한 셀의 흐름:
 * <ol>
 *   <li>progress = 기존 또는 신규 row</li>
 *   <li>이미 DONE 이면 skip (true 반환)</li>
 *   <li>IN_PROGRESS 마킹</li>
 *   <li>ASOS API 호출 (cutoff 적용 시 partial)</li>
 *   <li>응답 row → 시군구 fan-out → upsert</li>
 *   <li>DONE 마킹 (rowCount 저장)</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WeatherBackfillCellProcessor {

    private static final DateTimeFormatter TM_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final KmaAsosHourlyApiClient asosClient;
    private final WeatherObservationRepository observationRepository;
    private final WeatherBackfillProgressRepository progressRepository;

    /**
     * @return 이번 호출에서 적재한 시군구 row 수 (-1 이면 이미 DONE 으로 skip)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int processCell(
            YearMonth ym,
            String stnId,
            List<WeatherStationMapping> stnDistricts,
            LocalDate cutoffEndDate,   // null 이면 ym 의 말일 23시까지 전체
            Integer cutoffEndHour
    ) {
        WeatherBackfillProgressId pk = WeatherBackfillProgressId.of(ym.toString(), stnId);
        WeatherBackfillProgress progress = progressRepository.findById(pk)
                .orElse(null);

        if (progress != null && progress.getStatus() == BackfillStatus.DONE) {
            return -1;
        }

        if (progress == null) {
            progress = WeatherBackfillProgress.newInProgress(ym, stnId);
        } else {
            progress.markInProgress();
        }
        progressRepository.save(progress);

        AsosHourlyResponse response = isPartial(ym, cutoffEndDate)
                ? asosClient.fetchPartial(stnId, ym, cutoffEndDate.getDayOfMonth(), cutoffEndHour)
                : asosClient.fetchMonth(stnId, ym);

        int saved = 0;
        for (AsosHourlyResponse.Item item : response.items()) {
            LocalDateTime observedAt = parseTm(item.tm());
            if (observedAt == null) continue;
            // 안전망 — partial 인 경우에도 cutoff 넘는 row 가 섞여 들어오면 제외
            if (cutoffEndDate != null && observedAt.isAfter(cutoffEndDate.atTime(cutoffEndHour, 0))) {
                continue;
            }

            Double temperature = parseDoubleOrNull(item.ta());
            Double precipitation = parsePrecipOrNull(item.rn());
            Double windSpeed = parseDoubleOrNull(item.ws());
            Integer windDirection = parseIntOrNull(item.wd());
            Double humidity = parseDoubleOrNull(item.hm());
            // 기상 표준 컬럼: 해면기압(ps) 우선, 없으면 현지기압(pa). 둘 다 없으면 null.
            Double pressure = parseDoubleOrNull(item.ps());
            if (pressure == null) pressure = parseDoubleOrNull(item.pa());

            for (WeatherStationMapping district : stnDistricts) {
                observationRepository.upsert(
                        district.getLegalDistrictCode(),
                        observedAt,
                        temperature, precipitation,
                        windSpeed, windDirection,
                        humidity, pressure,
                        WeatherSource.ASOS_HISTORY.name()
                );
                saved++;
            }
        }

        progress.markDone(saved);
        progressRepository.save(progress);
        return saved;
    }

    /** 외부에서 호출하는 실패 마킹 — 새 트랜잭션으로 별도 commit. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCellFailed(YearMonth ym, String stnId, String message) {
        WeatherBackfillProgressId pk = WeatherBackfillProgressId.of(ym.toString(), stnId);
        WeatherBackfillProgress progress = progressRepository.findById(pk)
                .orElseGet(() -> WeatherBackfillProgress.newInProgress(ym, stnId));
        progress.markFailed(message);
        progressRepository.save(progress);
    }

    private boolean isPartial(YearMonth ym, LocalDate cutoffEndDate) {
        return cutoffEndDate != null && YearMonth.from(cutoffEndDate).equals(ym);
    }

    private LocalDateTime parseTm(String tm) {
        if (tm == null || tm.isBlank()) return null;
        try {
            return LocalDateTime.parse(tm.trim(), TM_FMT);
        } catch (Exception e) {
            log.warn("ASOS tm 파싱 실패: '{}'", tm);
            return null;
        }
    }

    private Double parseDoubleOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty() || "-".equals(t)) return null;
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseIntOrNull(String s) {
        Double d = parseDoubleOrNull(s);
        return d == null ? null : (int) Math.round(d);
    }

    /** 강수는 음수(-9, -99) 가 결측 의미로 오기도 함 — 음수 → null. */
    private Double parsePrecipOrNull(String s) {
        Double d = parseDoubleOrNull(s);
        if (d == null) return null;
        if (d < 0) return null;
        return d;
    }
}
