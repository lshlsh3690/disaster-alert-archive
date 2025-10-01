package com.disaster.alert.alertapi.domain.useralert.controller;

import com.disaster.alert.alertapi.domain.disasteralert.dto.UserAlertDtos;
import com.disaster.alert.alertapi.domain.member.model.MemberRole;
import com.disaster.alert.alertapi.domain.useralert.service.UserDisasterAlertService;
import com.disaster.alert.alertapi.global.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/user-alerts")
public class UserDisasterAlertController {
    private final UserDisasterAlertService userDisasterAlertService;

    /**
     * 생성
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ApiResponse<UserAlertDtos.Response> create(
            @AuthenticationPrincipal(expression = "id") Long memberId,
            @Valid @RequestBody UserAlertDtos.CreateRequest req
    ) {
        return ApiResponse.success(userDisasterAlertService.create(memberId, req));
    }


    // 단건 조회
    @GetMapping("/{id}")
    public ApiResponse<UserAlertDtos.Response> get(@PathVariable Long id) {
        return ApiResponse.success(userDisasterAlertService.get(id));
    }

    /**
     * 목록 조회
     * - ?mine=true 이면 본인 작성분만
     * - 기본 정렬: createdAt DESC
     */
    @GetMapping
    public ResponseEntity<Page<UserAlertDtos.Response>> list(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) Boolean mine,
            @AuthenticationPrincipal(expression = "id") Long memberId
    ) {
        if (Boolean.TRUE.equals(mine) && memberId == null) {
            // 비로그인 상태에서 mine=true 요청 방지
            return ResponseEntity.status(401).build();
        }
        Long ownerId = Boolean.TRUE.equals(mine) ? memberId : null;
        return ResponseEntity.ok(userDisasterAlertService.list(pageable, ownerId));
    }

    /**
     * 수정
     * - 작성자 본인 또는 ADMIN만 가능
     */
    @PreAuthorize("@userDisasterAlertService.isOwnerOrAdmin(#id, authentication.principal.id, authentication.principal.role)")
    @PatchMapping("/{id}")
    public ApiResponse<UserAlertDtos.Response> update(
            @PathVariable Long id,
            @AuthenticationPrincipal(expression = "id") Long memberId,
            @AuthenticationPrincipal(expression = "role") MemberRole role,
            @Valid @RequestBody UserAlertDtos.UpdateRequest req
    ) {
        return ApiResponse.success(userDisasterAlertService.update(id, req, memberId, role));
    }

    /**
     * 삭제
     * - 작성자 본인 또는 ADMIN만 가능
     */
    @PreAuthorize("@userDisasterAlertService.isOwnerOrAdmin(#id, authentication.principal.id, authentication.principal.role)")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal(expression = "id") Long memberId,
            @AuthenticationPrincipal(expression = "role") MemberRole role
    ) {
        userDisasterAlertService.delete(id, memberId, role);
        return ApiResponse.empty();
    }
}

