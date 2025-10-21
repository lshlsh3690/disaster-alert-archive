package com.disaster.alert.alertapi.domain.auth.service;

import com.disaster.alert.alertapi.domain.auth.dto.ReissueResponse;
import com.disaster.alert.alertapi.domain.common.exception.CustomException;
import com.disaster.alert.alertapi.domain.common.exception.ErrorCode;
import com.disaster.alert.alertapi.domain.member.dto.*;
import com.disaster.alert.alertapi.domain.member.model.Member;
import com.disaster.alert.alertapi.domain.member.service.MemberDetails;
import com.disaster.alert.alertapi.domain.member.service.MemberService;
import com.disaster.alert.alertapi.global.redis.RedisService;
import com.disaster.alert.alertapi.global.security.jwt.JwtTokenProvider;
import com.disaster.alert.alertapi.global.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final MemberService memberService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisService redisService;
    private final EmailService emailService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            MemberDetails memberDetails = (MemberDetails) authentication.getPrincipal();
            Member member = memberDetails.member();

            String accessToken = jwtTokenProvider.generateToken(memberDetails.member().getId(), memberDetails.getUsername(), memberDetails.member().getRole().name());
            String refreshToken = jwtTokenProvider.generateRefreshToken(member.getEmail());

            redisService.saveRefreshToken(member.getEmail(), refreshToken, jwtTokenProvider.getRefreshTokenExpiration() * 1000L);

            return LoginResponse.of(member, accessToken, refreshToken);
        } catch (UsernameNotFoundException e){
            throw new CustomException(ErrorCode.MEMBER_NOT_FOUND, "이메일로 회원을 찾을 수 없습니다.");
        } catch (AuthenticationException e) {
            throw new CustomException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
    }

    @Transactional(readOnly = true)
    public ReissueResponse reissue(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        String email = jwtTokenProvider.getClaims(refreshToken).getSubject();
        String storedToken = redisService.getRefreshToken(email);

        if (storedToken == null || !storedToken.equals(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Member member = memberService.findByEmail(email);

        String newAccessToken = jwtTokenProvider.generateToken(member.getId(), member.getEmail(), member.getRole().name());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(member.getEmail());

        long expiration = jwtTokenProvider.getRemainingExpiration(refreshToken);
        redisService.saveBlackListToken(refreshToken, expiration);
        redisService.deleteRefreshToken(email);
        redisService.saveRefreshToken(member.getEmail(), newRefreshToken, jwtTokenProvider.getRefreshTokenExpiration() * 1000L);

        return ReissueResponse.of(newAccessToken, newRefreshToken);
    }

    public void logout(String refreshToken) {
        // 로그아웃은 멱등하게 동작해야 하므로 토큰이 없거나 유효하지 않아도 성공 처리한다.
        if (refreshToken == null || refreshToken.isBlank()) {
            SecurityContextHolder.clearContext();
            return;
        }

        // 토큰이 유효하지 않으면 블랙리스트/삭제 없이 그대로 종료
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            SecurityContextHolder.clearContext();
            return;
        }

        String email = jwtTokenProvider.getClaims(refreshToken).getSubject();
        long expiration = jwtTokenProvider.getRemainingExpiration(refreshToken);
        redisService.saveBlackListToken(refreshToken, expiration);
        redisService.deleteRefreshToken(email);
        SecurityContextHolder.clearContext();
    }

    public void sendVerificationEmail(String email) {
        if (memberService.existsByEmail(email)) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }
        String code = generateRandomCode();
        redisService.setEmailVerificationCode(email, code, Duration.ofMinutes(3));
        log.info("이메일 인증 코드 생성: {} -> {}", email, code);
        emailService.send(email, "이메일 인증 코드", "인증 코드는: " + code);
    }

    private String generateRandomCode() {
        try {
            SecureRandom secureRandom = SecureRandom.getInstanceStrong();
            int upperLimit = (int) Math.pow(10, 6);
            int code = secureRandom.nextInt(upperLimit);
            return String.format("%06d", code); // 6자리 코드 생성
        } catch (Exception e) {
            throw new CustomException(ErrorCode.RANDOM_CODE_GENERATION_FAILED, e.getMessage());
        }
    }

    public void verifyEmailCode(String email, String code) {
        String storedCode = redisService.getEmailVerificationCode(email);

        if (storedCode == null || !storedCode.equals(code)) {
            throw new CustomException(ErrorCode.INVALID_EMAIL_VERIFICATION_CODE);
        }

        redisService.markEmailAsVerified(email);
    }
}
