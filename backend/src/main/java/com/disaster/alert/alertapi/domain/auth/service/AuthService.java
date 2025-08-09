package com.disaster.alert.alertapi.domain.auth.service;

import com.disaster.alert.alertapi.domain.auth.dto.ReissueRequest;
import com.disaster.alert.alertapi.domain.auth.dto.ReissueResponse;
import com.disaster.alert.alertapi.domain.member.dto.LoginRequest;
import com.disaster.alert.alertapi.domain.member.dto.LoginResponse;
import com.disaster.alert.alertapi.domain.member.dto.SignUpRequest;
import com.disaster.alert.alertapi.domain.member.dto.SignUpResponse;
import com.disaster.alert.alertapi.domain.member.model.Member;
import com.disaster.alert.alertapi.domain.member.service.MemberDetails;
import com.disaster.alert.alertapi.domain.member.service.MemberService;
import com.disaster.alert.alertapi.global.redis.RedisService;
import com.disaster.alert.alertapi.global.security.jwt.JwtTokenProvider;
import com.disaster.alert.alertapi.global.service.EmailService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.NoSuchAlgorithmException;
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

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Member member = memberService.findByEmail(request.getEmail());

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            MemberDetails memberDetails = (MemberDetails) authentication.getPrincipal();

            String accessToken = jwtTokenProvider.generateToken(memberDetails.member().getId(), memberDetails.getUsername(), memberDetails.member().getRole().name());
            String refreshToken = jwtTokenProvider.generateRefreshToken(member.getEmail());

            redisService.saveRefreshToken(member.getEmail(), refreshToken, jwtTokenProvider.getRefreshTokenExpiration() * 1000L);

            return LoginResponse.of(member, accessToken);
        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("이메일 또는 비밀번호가 일치하지 않습니다.");
        }
    }

    @Transactional(readOnly = true)
    public ReissueResponse reissue(ReissueRequest request) {
        String refreshToken = request.refreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new JwtException("Refresh Token 이 유효하지 않습니다.");
        }

        String email = jwtTokenProvider.getClaims(refreshToken).getSubject();
        String storedToken = redisService.getRefreshToken(email);

        if (storedToken == null || !storedToken.equals(refreshToken)) {
            throw new JwtException("Refresh Token 이 존재하지 않거나 일치하지 않습니다.");
        }

        Member member = memberService.findByEmail(email);

        String newAccessToken = jwtTokenProvider.generateToken(member.getId(), member.getEmail(), member.getRole().name());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(member.getEmail());

        redisService.saveRefreshToken(member.getEmail(), newRefreshToken, jwtTokenProvider.getRefreshTokenExpiration() * 1000L);

        return ReissueResponse.of(newAccessToken);
    }


    public SignUpResponse signUp(SignUpRequest request) {
        request.validatePasswordMatch();

        if (!redisService.isEmailVerified(request.getEmail())) {
            throw new IllegalStateException("이메일 인증이 완료되지 않았습니다.");
        }

        Long id = memberService.signUp(request);
        return new SignUpResponse(id, "회원가입이 완료되었습니다.");
    }

    public void logout(String email, String token) {
        if (redisService.hasKey(email)) {
            redisService.deleteRefreshToken(email);
        }

        // AccessToken 남은 시간 계산
        long expiration = jwtTokenProvider.getRemainingExpiration(token);

        // AccessToken 블랙리스트 등록
        redisService.saveBlackListToken(token, expiration);
        SecurityContextHolder.clearContext();
    }

    public void sendVerificationEmail(String email) {
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
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("랜덤 코드 생성에 실패했습니다.", e);
        }
    }

    public void verifyEmailCode(String email, String code) {
        String storedCode = redisService.getEmailVerificationCode(email);

        if (storedCode == null || !storedCode.equals(code)) {
            throw new IllegalArgumentException("인증 코드가 일치하지 않거나 만료되었습니다.");
        }
        redisService.deleteEmailVerificationCode(email);
        redisService.markEmailAsVerified(email);
    }
}
