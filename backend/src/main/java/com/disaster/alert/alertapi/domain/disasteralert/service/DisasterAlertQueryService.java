package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.domain.disasteralert.dto.AlertRecord;
import com.disaster.alert.alertapi.domain.disasteralert.dto.AlertSnLookup;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.disaster.alert.alertapi.domain.disasteralert.model.SenderLabel;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
     * sn 으로 알림을 찾아 요약(+ 속한 사건의 대표 sn)으로 돌려준다. 수집된 적 없는 sn 이면 빈 값(404 로 바꾸는 것은 호출한 쪽 몫).
     *
     * <p>엔티티를 밖으로 내보내지 않고 {@link AlertSnLookup} 으로 옮겨 담는다 — 다른 맥락이 지연 로딩 필드를 건드리지 않게.
     */
    @Transactional(readOnly = true)
    public Optional<AlertSnLookup> findBySn(Long sn) {
        // sn 은 UNIQUE 라 0행 또는 1행
        return disasterAlertRepository.findSnLookupRows(sn).stream()
                .findFirst()
                .map(DisasterAlertQueryService::toLookup);
    }

    /**
     * sn 으로 원문 기록을 찾는다(기록관 원문 영구 링크). 수집된 적 없는 sn 이면 빈 값.
     *
     * <p>발신 기관은 두 값을 함께 준다 — 인용문은 본문 표기 그대로({@code agencyLabel})를 쓰고, 기관 이름은 정규화 뒤
     * 표기와 달라질 수 있어 참고용이다. 기관이 지연 로딩이라 이 트랜잭션 안에서 꺼내 옮긴다.
     */
    @Transactional(readOnly = true)
    public Optional<AlertRecord> findRecordBySn(Long sn) {
        return disasterAlertRepository.findBySn(sn).map(a -> new AlertRecord(
                a.getId(),
                a.getSn(),
                a.getMessage(),
                a.getCreatedAt(),
                a.getModifiedDate(),
                a.getDisasterType(),
                a.getEmergencyLevel() == null ? null : a.getEmergencyLevel().getDescription(),
                a.getOriginalRegion(),
                a.getEventId(),
                a.getMessage() == null ? null : SenderLabel.extract(a.getMessage()).orElse(null),
                a.getAgency() == null ? null : a.getAgency().getName()));
    }

    /** 네이티브 쿼리 행(열 순서는 {@link DisasterAlertRepository#findSnLookupRows} javadoc)을 요약으로 옮긴다. */
    private static AlertSnLookup toLookup(Object[] r) {
        return new AlertSnLookup(
                toLong(r[0]),
                toLong(r[1]),
                (String) r[2],
                toLocalDateTime(r[3]),
                (String) r[4],
                // 긴급 단계는 enum 이름(LEVEL_1)으로 저장돼 있고, 화면·계약에는 표시 문구("안전안내")로 내려간다. 값이 없는 알림도 있다
                r[5] == null ? null : DisasterLevel.valueOf((String) r[5]).getDescription(),
                toLong(r[6]),
                toLong(r[7]));
    }

    private static Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    /** 네이티브 쿼리의 timestamp 열은 드라이버·하이버네이트 버전에 따라 Timestamp 또는 LocalDateTime 으로 온다. */
    private static LocalDateTime toLocalDateTime(Object value) {
        return value instanceof java.sql.Timestamp ts ? ts.toLocalDateTime() : (LocalDateTime) value;
    }
}
