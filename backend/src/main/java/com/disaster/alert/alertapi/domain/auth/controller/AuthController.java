package com.disaster.alert.alertapi.domain.auth.controller;

import com.disaster.alert.alertapi.common.dto.ApiResponse;
import com.disaster.alert.alertapi.domain.auth.dto.EmailCodeVerificationRequest;
import com.disaster.alert.alertapi.domain.auth.dto.EmailVerificationRequest;
import com.disaster.alert.alertapi.domain.auth.dto.ReissueRequest;
import com.disaster.alert.alertapi.domain.auth.dto.ReissueResponse;
import com.disaster.alert.alertapi.domain.auth.service.AuthService;
import com.disaster.alert.alertapi.domain.member.dto.LoginRequest;
import com.disaster.alert.alertapi.domain.member.dto.LoginResponse;
import com.disaster.alert.alertapi.domain.member.dto.SignUpRequest;
import com.disaster.alert.alertapi.domain.member.dto.SignUpResponse;
import com.disaster.alert.alertapi.domain.member.service.MemberDetails;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;


@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request,
                                               HttpServletResponse response) {
        LoginResponse loginResponse = authService.login(request);

        Cookie cookie = new Cookie("accessToken", loginResponse.getAccessToken());
        cookie.setHttpOnly(true);  // JS에서 접근 불가 (보안 강화)
        cookie.setSecure(false);    // HTTPS에서만 전송
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60); // 1시간

        response.addCookie(cookie);
        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/signup")
    public ResponseEntity<SignUpResponse> signUp(@RequestBody @Valid SignUpRequest request) {
        log.info(request.toString());
        return ResponseEntity.ok(authService.signUp(request));
    }

    @PostMapping("/reissue")
    public ResponseEntity<ReissueResponse> reissue(@RequestBody ReissueRequest request,
                                                   HttpServletResponse response) {
        ReissueResponse reissue = authService.reissue(request);

        Cookie cookie = new Cookie("accessToken", reissue.accessToken());
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // HTTPS에서만 전송
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60);

        response.addCookie(cookie);

        return ResponseEntity.ok(reissue);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal MemberDetails memberDetails,
                                       HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        String token = null;
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            token = bearerToken.substring(7);
        }

        authService.logout(memberDetails.getUsername(), token);
        return ResponseEntity.noContent().build();
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
