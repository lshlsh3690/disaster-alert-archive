package com.disaster.alert.alertapi.domain.event.controller;

import com.disaster.alert.alertapi.domain.event.dto.EventDetailResponse;
import com.disaster.alert.alertapi.domain.event.dto.EventListResponse;
import com.disaster.alert.alertapi.domain.event.dto.EventSearchRequest;
import com.disaster.alert.alertapi.domain.event.service.EventQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 재난 이벤트 조회 API.
 *
 * <p>이벤트 = 의미·지역·시간이 이어지는 재난문자 묶음(사건 단위).
 * {@code active} 는 조회 시점 파생값(마지막 알림 후 유형별 cooldown 이내 → 진행 중).
 */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Slf4j
public class EventController {

    private final EventQueryService eventQueryService;

    /**
     * 이벤트 목록 (페이지네이션, 최신순). 필터는 모두 선택적이라 기존 {@code active} 단독 호출과 호환.
     *
     * @param active       null=전체, true=진행 중만, false=지난 사건만
     * @param type         유형 (primary_disaster_type exact)
     * @param region       시도 또는 "시도 시군구" (primary_region_name prefix, 대표지역 기준 근사치)
     * @param districtCode 법정동 코드 (primary_region_code exact, 선택)
     * @param startDate    기간 시작 (yyyy-MM-dd, overlap)
     * @param endDate      기간 끝 (yyyy-MM-dd, overlap)
     * @param keyword      제목 키워드 (event_title contains)
     */
    @GetMapping
    public ResponseEntity<Page<EventListResponse>> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String districtCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "ko") String lang,
            Pageable pageable
    ) {
        EventSearchRequest req = EventSearchRequest.builder()
                .active(active)
                .type(type)
                .region(region)
                .districtCode(districtCode)
                .startDate(startDate)
                .endDate(endDate)
                .keyword(keyword)
                .build();
        return ResponseEntity.ok(eventQueryService.list(req, pageable, lang));
    }

    /**
     * 이벤트 상세 + 타임라인 (소속 재난문자 시간순). {@code lang} 으로 제목·타임라인 번역.
     */
    @GetMapping("/{id}")
    public ResponseEntity<EventDetailResponse> detail(
            @PathVariable Long id,
            @RequestParam(defaultValue = "ko") String lang
    ) {
        return ResponseEntity.ok(eventQueryService.detail(id, lang));
    }
}
