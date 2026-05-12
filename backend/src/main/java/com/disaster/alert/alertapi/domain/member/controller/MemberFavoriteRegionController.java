package com.disaster.alert.alertapi.domain.member.controller;

import com.disaster.alert.alertapi.domain.member.dto.MemberFavoriteRegionDtos;
import com.disaster.alert.alertapi.domain.member.model.MemberRole;
import com.disaster.alert.alertapi.domain.member.service.MemberFavoriteRegionService;
import com.disaster.alert.alertapi.global.dto.ApiResponse;
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
    public ResponseEntity<ApiResponse<List<MemberFavoriteRegionDtos.Response>>> getMyFavoriteRegions(
            @AuthenticationPrincipal(expression = "id") Long memberId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(memberFavoriteRegionService.getMyFavoriteRegions(memberId))
        );

    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<ApiResponse<MemberFavoriteRegionDtos.Response>> addFavoriteRegion(
            @AuthenticationPrincipal(expression = "id") Long memberId,
            @AuthenticationPrincipal(expression = "role") MemberRole role,
            @Valid @RequestBody MemberFavoriteRegionDtos.AddRequest request
    ) {
        // 프론트에서 관심지역을 추가한 후 메시지에, region Name을 활용할 수도 있으니, 객체까지 반환하도록 함
        MemberFavoriteRegionDtos.Response response = memberFavoriteRegionService.addFavoriteRegion(memberId, role, request.legalDistrictCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("관심 지역이 추가되었습니다.", response));
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{legalDistrictCode}")
    public ResponseEntity<ApiResponse<Void>> deleteFavoriteRegion(
            @AuthenticationPrincipal(expression = "id") Long memberId,
            @PathVariable String legalDistrictCode
    ) {
        memberFavoriteRegionService.deleteFavoriteRegion(memberId, legalDistrictCode);
        return ResponseEntity.ok(ApiResponse.success("관심 지역이 삭제되었습니다.", null));
    }
}