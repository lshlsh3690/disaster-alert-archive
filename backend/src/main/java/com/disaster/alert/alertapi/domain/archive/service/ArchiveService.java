package com.disaster.alert.alertapi.domain.archive.service;

import com.disaster.alert.alertapi.domain.archive.dto.ArchiveEventResolveResponse;
import com.disaster.alert.alertapi.domain.archive.dto.ArchiveMajorEventsResponse;
import com.disaster.alert.alertapi.domain.common.exception.CustomException;
import com.disaster.alert.alertapi.domain.common.exception.ErrorCode;
import com.disaster.alert.alertapi.domain.disasteralert.dto.AlertSnLookup;
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
 */
@Service
@RequiredArgsConstructor
public class ArchiveService {

    private final EventQueryService eventQueryService;
    private final DisasterAlertQueryService disasterAlertQueryService;

    /**
     * 연도별 주요 사건 — 포함 알림 수 내림차순 최대 10개. 유형으로 거르지 않는다(지속형 포함).
     *
     * @param year 조회 연도. null 이면 사건이 있는 가장 최근 연도
     * @return 사건이 하나도 없으면 연도·목록이 빈 응답(year 는 null)
     */
    public ArchiveMajorEventsResponse majorEvents(Integer year) {
        // 1) 연도 선택지는 사건이 실제로 있는 연도 — 화면이 시작 연도를 하드코딩하지 않게 한다
        List<Integer> years = eventQueryService.eventYears();

        // 2) year 가 없으면 가장 최근 연도. 사건이 아예 없으면 보여 줄 연도도 없다
        Integer target = year != null ? year : (years.isEmpty() ? null : years.get(0));
        if (target == null) {
            return new ArchiveMajorEventsResponse(null, years, List.of());
        }

        // 3) 정렬·상위 10·대표 sn 은 조회 쿼리가 정한다. 여기서는 응답 모양으로만 옮긴다
        List<ArchiveMajorEventsResponse.Item> items = eventQueryService.majorEvents(target).stream()
                .map(r -> new ArchiveMajorEventsResponse.Item(
                        r.representativeSn(), r.eventTitle(), r.primaryDisasterType(), r.primaryRegionName(),
                        r.firstAlertAt(), r.lastAlertAt(), r.alertCount()))
                .toList();
        return new ArchiveMajorEventsResponse(target, years, items);
    }

    /**
     * sn 을 현재 사건으로 해석한다. 사건에 묶였으면 bound=true + 사건 ID + 대표 sn, 아니면 bound=false + 알림 요약.
     *
     * @throws CustomException 수집된 적 없는 sn (DISASTER_ALERT_NOT_FOUND, 4xx 라 warn 이하로만 기록된다)
     */
    public ArchiveEventResolveResponse resolveEvent(Long sn) {
        // 1) 없는 sn 은 404 — 링크가 잘못됐거나 수집된 적 없는 문자
        AlertSnLookup alert = disasterAlertQueryService.findBySn(sn)
                .orElseThrow(() -> new CustomException(ErrorCode.DISASTER_ALERT_NOT_FOUND));

        // 2) 사건에 안 묶인 알림은 오류가 아니라 정상 상태(비사건 분류 알림은 원래 묶이지 않는다) — 원문 요약만 준다
        if (alert.eventId() == null) {
            return new ArchiveEventResolveResponse(sn, false, null, null, new ArchiveEventResolveResponse.Alert(
                    alert.alertId(), alert.sn(), alert.message(), alert.createdAt(),
                    alert.disasterType(), alert.emergencyLevel()));
        }

        // 3) 묶였으면 그 알림이 대표가 아니어도(사건이 합쳐진 경우) 지금 속한 사건으로 해석한다.
        //    화면에 보일 영구 링크는 항상 현재 대표 sn 으로 만든다
        Long representativeSn = eventQueryService.representativeSn(alert.eventId());
        return new ArchiveEventResolveResponse(sn, true, alert.eventId(), representativeSn, null);
    }
}
