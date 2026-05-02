package com.disaster.alert.alertapi.domain.comment;

import com.disaster.alert.alertapi.domain.comment.dto.CommentDtos;
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
@RequestMapping("/api/v1/comments")
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    public ResponseEntity<Page<CommentDtos.Response>> list(
            @RequestParam String source,
            @RequestParam Long targetId,
            @PageableDefault(sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(commentService.list(source, targetId, pageable));
    }

    @GetMapping("/latest")
    public ResponseEntity<Page<CommentDtos.Response>> latest(
            @PageableDefault(sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false, defaultValue = "5") Integer size
    ) {
        Pageable p = org.springframework.data.domain.PageRequest.of(0, size, pageable.getSort());
        return ResponseEntity.ok(commentService.latest(p));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ApiResponse<CommentDtos.Response> create(
            @AuthenticationPrincipal(expression = "id") Long memberId,
            @Valid @RequestBody CommentDtos.CreateRequest req
    ) {
        return ApiResponse.success(commentService.create(memberId, req));
    }

    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{id}")
    public ApiResponse<CommentDtos.Response> update(
            @PathVariable Long id,
            @AuthenticationPrincipal(expression = "id") Long memberId,
            @AuthenticationPrincipal(expression = "role") MemberRole role,
            @Valid @RequestBody CommentDtos.UpdateRequest req
    ) {
        return ApiResponse.success(commentService.update(id, req, memberId, role));
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal(expression = "id") Long memberId,
            @AuthenticationPrincipal(expression = "role") MemberRole role
    ) {
        commentService.delete(id, memberId, role);
        return ResponseEntity.noContent().build();
    }
}


