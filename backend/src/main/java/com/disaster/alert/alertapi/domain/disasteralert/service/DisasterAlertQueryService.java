package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.domain.disasteralert.dto.AlertSnLookup;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 알림 조회 전용 서비스 — 다른 맥락(기록관 등)이 알림을 읽을 때 거치는 입구.
 *
 * <p>874줄짜리 {@code DisasterAlertService}(수집·번역·통계 혼재)에 조회를 더하지 않으려고 분리했다(헌법 VII).
 *
 * <p>Red 단계 뼈대 — 구현은 Green 에서.
 */
@Service
@RequiredArgsConstructor
public class DisasterAlertQueryService {

    private final DisasterAlertRepository disasterAlertRepository;

    /** sn 으로 알림을 찾는다. 수집된 적 없는 sn 이면 빈 값. */
    // Red 단계 뼈대 — 구현은 Green 에서
    public Optional<AlertSnLookup> findBySn(Long sn) {
        return Optional.empty();
    }
}
