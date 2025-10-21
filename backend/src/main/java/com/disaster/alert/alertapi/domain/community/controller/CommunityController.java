package com.disaster.alert.alertapi.domain.community.controller;

import com.disaster.alert.alertapi.domain.community.dto.CommunityDtos;
import com.disaster.alert.alertapi.domain.community.model.CommunityPostCategory;
import com.disaster.alert.alertapi.domain.community.service.CommunityService;
import com.disaster.alert.alertapi.domain.member.model.MemberRole;
import com.disaster.alert.alertapi.global.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/community")
public class CommunityController {
    private final CommunityService service;

    @GetMapping
    public ResponseEntity<Page<CommunityDtos.Response>> list(
            @RequestParam CommunityPostCategory category,
            @PageableDefault(sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(service.list(category, pageable));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ApiResponse<CommunityDtos.Response> create(
            @AuthenticationPrincipal(expression = "id") Long memberId,
            @AuthenticationPrincipal(expression = "role") MemberRole role,
            @Valid @RequestBody CommunityDtos.CreateRequest req
    ) {
        return ApiResponse.success(service.create(memberId, role, req));
    }
}


