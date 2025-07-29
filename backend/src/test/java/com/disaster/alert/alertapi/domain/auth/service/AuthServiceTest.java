package com.disaster.alert.alertapi.domain.auth.service;

import com.disaster.alert.alertapi.domain.auth.dto.ReissueRequest;
import com.disaster.alert.alertapi.domain.auth.dto.ReissueResponse;
import com.disaster.alert.alertapi.domain.member.dto.LoginRequest;
import com.disaster.alert.alertapi.domain.member.dto.LoginResponse;
import com.disaster.alert.alertapi.domain.member.dto.SignUpRequest;
import com.disaster.alert.alertapi.domain.member.dto.SignUpResponse;
import com.disaster.alert.alertapi.global.redis.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Slf4j
@ActiveProfiles("test")
class AuthServiceTest {
    @Autowired
    private AuthService authService;

    @Autowired
    private RedisService redisService;

    @Test
    void signup()
    {
        SignUpRequest signUpRequest = SignUpRequest.builder()
                .email("asdf3@naver.com")
                .password("asdf3")
                .nickname("asdf3")
                .name("홍길동")
                .build();
        SignUpResponse signUpResponse = authService.signUp(signUpRequest);
        log.info("SignUpResponse: {}", signUpResponse);
    }

    @Test
    void reissue() {
        LoginRequest loginRequest = new LoginRequest("asdf3@naver.com", "asdf3");
        LoginResponse login = authService.login(loginRequest);

        boolean b = redisService.hasKey(login.getEmail());
        assertTrue(b, "Redis에 Refresh Token이 저장되어야 합니다.");

        ReissueRequest reissueRequest = new ReissueRequest(redisService.getRefreshToken(login.getEmail()));
        ReissueResponse reissue = authService.reissue(reissueRequest);

        log.info("ReissueResponse: {}", reissue);
    }
}