package com.disaster.alert.alertapi.domain.member.controller;

import com.disaster.alert.alertapi.domain.member.dto.MemberFavoriteRegionDtos;
import com.disaster.alert.alertapi.domain.member.model.MemberRole;
import com.disaster.alert.alertapi.domain.member.service.MemberFavoriteRegionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members/me/favorite-regions")
public class MemberFavoriteRegionController {

    private final MemberFavoriteRegionService memberFavoriteRegionService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<List<MemberFavoriteRegionDtos.Response>> getMyFavoriteRegions(
            @AuthenticationPrincipal(expression = "id") Long memberId
    ) {
        return ResponseEntity.ok(memberFavoriteRegionService.getMyFavoriteRegions(memberId));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<Void> addFavoriteRegion(
            @AuthenticationPrincipal(expression = "id") Long memberId,
            @AuthenticationPrincipal(expression = "role") MemberRole role,
            @Valid @RequestBody MemberFavoriteRegionDtos.AddRequest request
    ) {
        memberFavoriteRegionService.addFavoriteRegion(memberId, role, request.legalDistrictCode());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{legalDistrictCode}")
    public ResponseEntity<Void> deleteFavoriteRegion(
            @AuthenticationPrincipal(expression = "id") Long memberId,
            @PathVariable String legalDistrictCode
    ) {
        memberFavoriteRegionService.deleteFavoriteRegion(memberId, legalDistrictCode);
        return ResponseEntity.noContent().build();
    }
}