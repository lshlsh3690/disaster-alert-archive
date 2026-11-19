package com.disaster.alert.alertapi.domain.archive.controller;

import com.disaster.alert.alertapi.domain.archive.dto.ArchiveEventResolveResponse;
import com.disaster.alert.alertapi.domain.archive.service.ArchiveService;
import com.disaster.alert.alertapi.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사건 영구 링크 API — 로그인 없이 GET 으로 읽는다(계약: specs/007-disaster-archive/contracts/archive-api.md).
 *
 * <p>사건 링크는 사건 ID 가 아니라 대표 알림 sn 으로 만든다. 사건 ID 는 백필 때 지우고 다시 매겨져 영구하지 않다.
 */
@RestController
@RequestMapping("/api/v1/archive")
@RequiredArgsConstructor
public class ArchiveController {

    private final ArchiveService archiveService;

    /** 사건 영구 링크 해석 — sn 알림이 지금 속한 사건. 없는 sn 은 404(DISASTER_ALERT_NOT_FOUND). */
    @GetMapping("/events/{sn}")
    public ResponseEntity<ApiResponse<ArchiveEventResolveResponse>> resolveEvent(@PathVariable Long sn) {
        return ResponseEntity.ok(ApiResponse.success(archiveService.resolveEvent(sn)));
    }
}
