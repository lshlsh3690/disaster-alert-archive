package com.disaster.alert.alertapi.domain.auth.service;

import com.disaster.alert.alertapi.domain.member.dto.LoginRequest;
import com.disaster.alert.alertapi.domain.member.dto.LoginResponse;
import com.disaster.alert.alertapi.domain.member.dto.SignUpRequest;
import com.disaster.alert.alertapi.domain.member.dto.SignUpResponse;
import com.disaster.alert.alertapi.domain.member.model.Member;
import com.disaster.alert.alertapi.domain.member.repository.MemberRepository;
import com.disaster.alert.alertapi.domain.member.service.MemberDetails;
import com.disaster.alert.alertapi.domain.member.service.MemberService;
import com.disaster.alert.alertapi.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final MemberService memberService;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Member member = memberService.findByEmail(request.getEmail());

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            MemberDetails memberDetails = (MemberDetails) authentication.getPrincipal();

            String accessToken = jwtTokenProvider.generateToken(memberDetails.member().getId(), memberDetails.getUsername(), memberDetails.member().getRole().name());
//            String refreshToken = jwtTokenProvider.generateToken(loginRequest.getUsername());
//            redisService.saveRefreshToken(loginRequest.getUsername(), refreshToken, 60 * 60 * 7);

            return LoginResponse.of(member, accessToken, null);
        } catch (BadCredentialsException e) {
//            throw new CustomException(ErrorCode.LOGIN_FAILED);
            throw new BadCredentialsException("이메일 또는 비밀번호가 일치하지 않습니다.");
        }
    }

    public SignUpResponse signUp(SignUpRequest request) {
        Long id = memberService.signUp(request);
        return new SignUpResponse(id, "회원가입이 완료되었습니다.");
    }
}
