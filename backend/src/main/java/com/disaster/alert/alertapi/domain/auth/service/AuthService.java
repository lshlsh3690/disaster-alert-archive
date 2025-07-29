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
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final MemberService memberService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisService redisService;

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

            return LoginResponse.of(member, accessToken, refreshToken);
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

        return ReissueResponse.of(newAccessToken, newRefreshToken);
    }


    public SignUpResponse signUp(SignUpRequest request) {
        Long id = memberService.signUp(request);
        return new SignUpResponse(id, "회원가입이 완료되었습니다.");
    }
}
