package com.disaster.alert.alertapi.domain.event.controller;

import com.disaster.alert.alertapi.domain.event.dto.EventDetailResponse;
import com.disaster.alert.alertapi.domain.event.dto.EventListResponse;
import com.disaster.alert.alertapi.domain.event.service.EventQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
     * 이벤트 목록 (페이지네이션, 최신순).
     *
     * @param active null=전체, true=진행 중만, false=지난 사건만
     */
    @GetMapping
    public ResponseEntity<Page<EventListResponse>> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "ko") String lang,
            Pageable pageable
    ) {
        return ResponseEntity.ok(eventQueryService.list(active, pageable, lang));
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
