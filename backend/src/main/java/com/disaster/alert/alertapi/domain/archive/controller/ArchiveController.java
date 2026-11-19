package com.disaster.alert.alertapi.domain.archive.controller;

import com.disaster.alert.alertapi.domain.archive.dto.ArchiveAlertResponse;
import com.disaster.alert.alertapi.domain.archive.dto.ArchiveEventResolveResponse;
import com.disaster.alert.alertapi.domain.archive.dto.ArchiveMajorEventsResponse;
import com.disaster.alert.alertapi.domain.archive.service.ArchiveService;
import com.disaster.alert.alertapi.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 재난 기록관 API — 로그인 없이 GET 으로 읽는 영구 기록(계약: specs/007-disaster-archive/contracts/archive-api.md).
 *
 * <p>사건 링크는 사건 ID 가 아니라 대표 알림 sn 으로 만든다. 사건 ID 는 백필 때 지우고 다시 매겨져 영구하지 않다.
 */
@RestController
@RequestMapping("/api/v1/archive")
@RequiredArgsConstructor
public class ArchiveController {

    private final ArchiveService archiveService;

    /** 연도별 주요 사건(알림 수 상위 10). year 를 빼면 사건이 있는 가장 최근 연도. */
    @GetMapping("/major-events")
    public ResponseEntity<ApiResponse<ArchiveMajorEventsResponse>> majorEvents(
            @RequestParam(required = false) Integer year
    ) {
        return ResponseEntity.ok(ApiResponse.success(archiveService.majorEvents(year)));
    }

    /** 원문 기록 — sn 으로 원문 알림 한 건(발신 기관 표기 포함). 없는 sn 은 404(DISASTER_ALERT_NOT_FOUND). */
    @GetMapping("/alerts/{sn}")
    public ResponseEntity<ApiResponse<ArchiveAlertResponse>> alertRecord(@PathVariable Long sn) {
        return ResponseEntity.ok(ApiResponse.success(archiveService.alertRecord(sn)));
    }

    /** 사건 영구 링크 해석 — sn 알림이 지금 속한 사건. 없는 sn 은 404(DISASTER_ALERT_NOT_FOUND). */
    @GetMapping("/events/{sn}")
    public ResponseEntity<ApiResponse<ArchiveEventResolveResponse>> resolveEvent(@PathVariable Long sn) {
        return ResponseEntity.ok(ApiResponse.success(archiveService.resolveEvent(sn)));
    }
}
