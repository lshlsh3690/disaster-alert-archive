package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.domain.disasteralert.dto.AlertSnLookup;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 알림 조회 전용 서비스 — 다른 맥락(기록관 등)이 알림을 읽을 때 거치는 입구.
 *
 * <p>874줄짜리 {@code DisasterAlertService}(수집·번역·통계 혼재)에 조회를 더하지 않으려고 분리했다(헌법 VII).
 */
@Service
@RequiredArgsConstructor
public class DisasterAlertQueryService {

    private final DisasterAlertRepository disasterAlertRepository;

    /**
     * sn 으로 알림을 찾아 요약으로 돌려준다. 수집된 적 없는 sn 이면 빈 값(404 로 바꾸는 것은 호출한 쪽 몫).
     *
     * <p>엔티티를 밖으로 내보내지 않고 {@link AlertSnLookup} 으로 옮겨 담는다 — 다른 맥락이 지연 로딩 필드를 건드리지 않게.
     */
    @Transactional(readOnly = true)
    public Optional<AlertSnLookup> findBySn(Long sn) {
        return disasterAlertRepository.findBySn(sn).map(DisasterAlertQueryService::toLookup);
    }

    private static AlertSnLookup toLookup(DisasterAlert a) {
        return new AlertSnLookup(
                a.getId(),
                a.getSn(),
                a.getMessage(),
                a.getCreatedAt(),
                a.getDisasterType(),
                // 긴급 단계는 화면·계약에 표시 문구("안전안내")로 내려간다. 값이 없는 알림도 있다
                a.getEmergencyLevel() == null ? null : a.getEmergencyLevel().getDescription(),
                // 사건에 묶이지 않았으면 null — 지연 프록시라도 id 는 조회 없이 읽힌다
                a.getEventId());
    }
}
