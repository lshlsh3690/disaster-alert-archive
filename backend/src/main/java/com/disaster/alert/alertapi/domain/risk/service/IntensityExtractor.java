package com.disaster.alert.alertapi.domain.risk.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 재난문자 본문에서 강도 수치 추출.
 *
 * <p>정부 재난문자는 양식이 정형화되어 정규식 적중률이 높다(~95%).
 * 매칭 실패 시 empty 반환 → 호출 측에서 multiplier 1.0 (강도 미반영).
 *
 * <p>강도가 weight/half-life/spread 까지 보정하는 건 Phase 2. 여기서는 weight multiplier 만.
 */
@Component
public class IntensityExtractor {

    private static final Map<String, Pattern> PATTERNS = Map.of(
            "지진", Pattern.compile("규모\\s*(\\d+\\.?\\d*)"),
            "호우", Pattern.compile("시간당\\s*(\\d+)\\s*(?:밀리|mm|㎜)"),
            "폭염", Pattern.compile("(?:체감|기온|최고)\\s*(\\d+)\\s*도"),
            "한파", Pattern.compile("(?:체감|최저)\\s*-?\\s*(\\d+)\\s*도"),
            "산불", Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:헥타|ha|㏊)"),
            "강풍", Pattern.compile("초속\\s*(\\d+(?:\\.\\d+)?)\\s*(?:미터|m)")
    );

    /** 알림 본문에서 유형에 맞는 강도값 추출. 한파는 절대값으로 반환(구간이 절대값 기준). */
    public Optional<Double> extract(DisasterAlert alert) {
        String type = alert.getDisasterType();
        String message = alert.getMessage();
        if (type == null || message == null) return Optional.empty();

        Pattern p = PATTERNS.get(type);
        if (p == null) return Optional.empty();

        Matcher m = p.matcher(message);
        if (!m.find()) return Optional.empty();

        try {
            return Optional.of(Double.parseDouble(m.group(1)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
