package com.disaster.alert.alertapi.domain.archive.service;

import com.disaster.alert.alertapi.domain.archive.dto.ArchiveEventResolveResponse;
import com.disaster.alert.alertapi.domain.archive.dto.ArchiveMajorEventsResponse;
import com.disaster.alert.alertapi.domain.disasteralert.service.DisasterAlertQueryService;
import com.disaster.alert.alertapi.domain.event.service.EventQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 재난 기록관 — 사건·원문을 영구 링크로 인용할 수 있게 하는 조회 전용 서비스.
 *
 * <p>사건 번호는 재클러스터링 때 바뀌므로 링크는 원 출처 고유번호(sn)로 만든다.
 * 리포지토리를 직접 쓰지 않고 맥락별 조회 서비스를 거친다(헌법 VII).
 *
 * <p>Red 단계 뼈대 — 구현은 Green 에서.
 */
// Red 단계 뼈대 — 구현은 Green 에서
@Service
@RequiredArgsConstructor
public class ArchiveService {

    private final EventQueryService eventQueryService;
    private final DisasterAlertQueryService disasterAlertQueryService;

    /**
     * 연도별 주요 사건 — 지속형 유형 제외, 포함 알림 수 내림차순 최대 10개.
     *
     * @param year 조회 연도. null 이면 사건이 있는 가장 최근 연도
     */
    public ArchiveMajorEventsResponse majorEvents(Integer year) {
        return new ArchiveMajorEventsResponse(year, List.of(), List.of());
    }

    /**
     * sn 을 현재 사건으로 해석한다. 사건에 묶였으면 bound=true + 사건 ID + 대표 sn, 아니면 bound=false.
     *
     * @throws com.disaster.alert.alertapi.domain.common.exception.CustomException 수집된 적 없는 sn (DISASTER_ALERT_NOT_FOUND)
     */
    public ArchiveEventResolveResponse resolveEvent(Long sn) {
        return new ArchiveEventResolveResponse(sn, false, null, null, null);
    }
}
