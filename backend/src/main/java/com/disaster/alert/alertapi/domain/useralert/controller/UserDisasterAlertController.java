package com.disaster.alert.alertapi.domain.useralert.controller;

import com.disaster.alert.alertapi.domain.disasteralert.dto.UserAlertDtos;
import com.disaster.alert.alertapi.domain.useralert.service.UserDisasterAlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/user-alerts")
public class UserDisasterAlertController {
    private final UserDisasterAlertService userDisasterAlertService;

    /**
     * 생성
     */
    @PostMapping
    public ResponseEntity<UserAlertDtos.Response> create(
            @AuthenticationPrincipal(expression = "id") Long memberId, // 커스텀 UserDetails에 id 필드가 있다고 가정
            @Valid @RequestBody UserAlertDtos.CreateRequest req
    ) {
        // 비로그인 보호는 Security 설정(permitAll/Authenticated)로 걸어두는 게 베스트
        UserAlertDtos.Response created = userDisasterAlertService.create(memberId, req);
        return ResponseEntity
                .created(URI.create("/api/v1/user-alerts/" + created.getId()))
                .body(created);
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
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserAlertDtos.Response> update(
            @PathVariable Long id,
            @AuthenticationPrincipal(expression = "id") Long memberId,
            Authentication authentication,
            @Valid @RequestBody UserAlertDtos.UpdateRequest req
    ) {
        boolean isAdmin = hasAdmin(authentication);
        return ResponseEntity.ok(userDisasterAlertService.update(id, memberId, req, isAdmin));
    }

    /**
     * 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal(expression = "id") Long memberId,
            Authentication authentication
    ) {
        boolean isAdmin = hasAdmin(authentication);
        userDisasterAlertService.delete(id, memberId, isAdmin);
        return ResponseEntity.noContent().build();
    }

    private boolean hasAdmin(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}

