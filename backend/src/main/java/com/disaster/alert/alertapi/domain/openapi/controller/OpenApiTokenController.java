package com.disaster.alert.alertapi.domain.openapi.controller;

import com.disaster.alert.alertapi.domain.member.service.MemberDetails;
import com.disaster.alert.alertapi.domain.openapi.dto.OpenApiTokenDtos;
import com.disaster.alert.alertapi.domain.openapi.service.OpenApiTokenService;
import com.disaster.alert.alertapi.global.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/open-api/tokens")
/**
 * OpenAPI 서비스키 관리 컨트롤러.
 *
 * <p>외부 데이터 조회용 서비스키를 발급, 조회, 폐기한다.
 * 이 API는 서비스키 자체가 아니라 로그인한 회원의 JWT 인증으로 접근해야 하므로
 * 실제 데이터 조회 API와 보안 체인을 분리했다.
 */
public class OpenApiTokenController {
    /** 서비스키 발급, 조회, 폐기 유스케이스를 처리하는 서비스. */
    private final OpenApiTokenService openApiTokenService;

    /**
     * 로그인한 회원에게 새 OpenAPI 서비스키를 발급한다.
     *
     * <p>원본 토큰은 이 응답에서 한 번만 반환되고, DB에는 해시만 저장된다.
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<ApiResponse<OpenApiTokenDtos.CreateResponse>> create(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @Valid @RequestBody OpenApiTokenDtos.CreateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(openApiTokenService.create(memberDetails.getId(), request)));
    }

    /** 로그인한 회원이 발급받은 OpenAPI 서비스키 목록을 조회한다. 원본 토큰은 반환하지 않는다. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<ApiResponse<List<OpenApiTokenDtos.Response>>> findMine(
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        return ResponseEntity.ok(ApiResponse.success(openApiTokenService.findMine(memberDetails.getId())));
    }

    /** 로그인한 회원이 소유한 OpenAPI 서비스키를 폐기한다. 폐기된 키는 이후 데이터 조회에 사용할 수 없다. */
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{tokenId}")
    public ResponseEntity<ApiResponse<Void>> revoke(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @PathVariable Long tokenId
    ) {
        openApiTokenService.revoke(memberDetails.getId(), tokenId);
        return ResponseEntity.ok(ApiResponse.empty());
    }
}
