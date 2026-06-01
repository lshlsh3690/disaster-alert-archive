package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.common.exception.CustomException;
import com.disaster.alert.alertapi.domain.common.exception.ErrorCode;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.event.dto.EventAlertItem;
import com.disaster.alert.alertapi.domain.event.dto.EventDetailResponse;
import com.disaster.alert.alertapi.domain.event.dto.EventListResponse;
import com.disaster.alert.alertapi.domain.event.dto.EventTimelineRow;
import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import com.disaster.alert.alertapi.domain.event.repository.DisasterEventRepository;
import com.disaster.alert.alertapi.domain.event.repository.EventAlertMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 이벤트 조회 서비스.
 *
 * <p>{@code active}(진행중/지난사건)는 저장값이 아니라 조회 시점 KST 기준 파생값.
 * 운영 JVM 이 UTC 일 수 있어 항상 {@code Asia/Seoul} 로 now 를 만든다 (DB now() 안 씀).
 */
@Service
@RequiredArgsConstructor
public class EventQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DisasterEventRepository disasterEventRepository;
    private final EventAlertMappingRepository eventAlertMappingRepository;
    private final DisasterAlertRepository disasterAlertRepository;

    /**
     * 이벤트 목록.
     *
     * @param active null=전체, true=진행 중만, false=지난 사건만
     */
    @Transactional(readOnly = true)
    public Page<EventListResponse> list(Boolean active, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now(KST);
        // 정렬은 쿼리에 고정(last_alert_at DESC). native 쿼리 + Pageable sort 충돌 방지 위해 page/size 만.
        Pageable pageOnly = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        Page<DisasterEvent> page;
        if (active == null) {
            page = disasterEventRepository.findAllByOrderByLastAlertAtDesc(pageOnly);
        } else if (active) {
            page = disasterEventRepository.findActive(now, pageOnly);
        } else {
            page = disasterEventRepository.findInactive(now, pageOnly);
        }
        return page.map(e -> EventListResponse.of(e, now));
    }

    /**
     * 이벤트 상세 + 타임라인.
     */
    @Transactional(readOnly = true)
    public EventDetailResponse detail(Long id) {
        LocalDateTime now = LocalDateTime.now(KST);
        DisasterEvent event = disasterEventRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.EVENT_NOT_FOUND, "id=" + id));

        List<EventTimelineRow> rows = eventAlertMappingRepository.findTimelineRows(id);
        Map<Long, List<String>> regionNamesByAlert = loadRegionNames(rows);

        List<EventAlertItem> timeline = new ArrayList<>(rows.size());
        for (EventTimelineRow r : rows) {
            timeline.add(new EventAlertItem(
                    r.alertId(),
                    r.sequenceNo(),
                    r.message(),
                    r.disasterType(),
                    r.emergencyLevel() == null ? null : r.emergencyLevel().getDescription(),
                    r.createdAt(),
                    regionNamesByAlert.getOrDefault(r.alertId(), List.of())
            ));
        }
        return EventDetailResponse.of(event, now, timeline);
    }

    /** 타임라인 알림들의 지역명을 batch 로 조회해 alertId → names 로 묶음. */
    private Map<Long, List<String>> loadRegionNames(List<EventTimelineRow> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> alertIds = rows.stream().map(EventTimelineRow::alertId).toList();
        Map<Long, List<String>> map = new HashMap<>();
        for (Object[] pair : disasterAlertRepository.findAlertIdAndRegionNamePairs(alertIds)) {
            Long alertId = (Long) pair[0];
            String name = (String) pair[1];
            map.computeIfAbsent(alertId, k -> new ArrayList<>()).add(name);
        }
        return map;
    }
}
