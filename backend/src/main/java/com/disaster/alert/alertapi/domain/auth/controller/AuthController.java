package com.disaster.alert.alertapi.domain.auth.controller;

import com.disaster.alert.alertapi.domain.common.exception.CustomException;
import com.disaster.alert.alertapi.domain.common.exception.ErrorCode;
import com.disaster.alert.alertapi.domain.member.dto.*;
import com.disaster.alert.alertapi.domain.member.service.MemberService;
import com.disaster.alert.alertapi.global.dto.ApiResponse;
import com.disaster.alert.alertapi.domain.auth.dto.EmailCodeVerificationRequest;
import com.disaster.alert.alertapi.domain.auth.dto.EmailVerificationRequest;
import com.disaster.alert.alertapi.domain.auth.dto.ReissueResponse;
import com.disaster.alert.alertapi.domain.auth.service.AuthService;
import com.disaster.alert.alertapi.global.util.CookieUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;


import java.time.Duration;


@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final AuthService authService;
    private final MemberService memberService;
    @org.springframework.beans.factory.annotation.Value("${cookie.secure:true}")
    private boolean isSecureCookie; // 운영 true, 로컬 false

    @Value("${cookie.domain:}")
    private String cookieDomain;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletResponse response) {
        LoginResponse loginResponse = authService.login(request);

        var accessCookie = CookieUtil.buildCookie("accessToken", loginResponse.getAccessToken(), Duration.ofHours(1), isSecureCookie, cookieDomain);
        var refreshCookie = CookieUtil.buildCookie("refreshToken", loginResponse.getRefreshToken(), Duration.ofDays(7), isSecureCookie,cookieDomain);

        response.addHeader("Set-Cookie", accessCookie.toString());
        response.addHeader("Set-Cookie", refreshCookie.toString());

        return ResponseEntity.ok(ApiResponse.success(
                loginResponse
        ));
    }

    @PostMapping("/signup")
    public ApiResponse<MemberDtos.Response> signUp(@Valid @RequestBody MemberDtos.CreateRequest request) {
        log.info(request.toString());
        return ApiResponse.success(
                memberService.create(request)
        );
    }

    @PostMapping("/reissue")
    public ResponseEntity<ReissueResponse> reissue(@CookieValue(value = "refreshToken", required = false) String refreshToken,
                                                   HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        ReissueResponse reissue = authService.reissue(refreshToken);

        var accessCookie = CookieUtil.buildCookie("accessToken", reissue.accessToken(), Duration.ofHours(1), isSecureCookie,cookieDomain);
        var refreshCookie = CookieUtil.buildCookie("refreshToken", reissue.refreshToken(), Duration.ofDays(7), isSecureCookie,cookieDomain);

        response.addHeader("Set-Cookie", accessCookie.toString());
        response.addHeader("Set-Cookie", refreshCookie.toString());

        return ResponseEntity.ok(reissue);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@CookieValue(value = "refreshToken", required = false) String refreshToken,
                                       HttpServletResponse response) {
        authService.logout(refreshToken);
        var expired = Duration.ZERO;
        response.addHeader("Set-Cookie", CookieUtil.buildCookie("accessToken",  "", expired, isSecureCookie,cookieDomain).toString());
        response.addHeader("Set-Cookie", CookieUtil.buildCookie("refreshToken", "", expired, isSecureCookie,cookieDomain).toString());
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<Void>> sendVerificationEmail(@RequestBody @Valid EmailVerificationRequest request) {
        authService.sendVerificationEmail(request.email());
        return ResponseEntity.ok(ApiResponse.success(
                "인증 코드가 이메일로 전송되었습니다. 이메일을 확인해주세요.",
                null
        ));
    }

    @PostMapping("/email/verify/code")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestBody EmailCodeVerificationRequest request) {
        authService.verifyEmailCode(request.email(), request.code());
        return ResponseEntity.ok(ApiResponse.success(
                "이메일 인증이 완료되었습니다.",
                null
        ));
    }
}
