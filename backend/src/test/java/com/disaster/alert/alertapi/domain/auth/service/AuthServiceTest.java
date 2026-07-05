package com.disaster.alert.alertapi.domain.auth.service;

import com.disaster.alert.alertapi.domain.auth.dto.ReissueRequest;
import com.disaster.alert.alertapi.domain.auth.dto.ReissueResponse;
import com.disaster.alert.alertapi.domain.member.dto.*;
import com.disaster.alert.alertapi.domain.member.service.MemberService;
import com.disaster.alert.alertapi.global.redis.RedisService;
import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
@Slf4j
class AuthServiceTest {
    @Autowired
    private AuthService authService;

    @Autowired
    private RedisService redisService;
    @Autowired
    private MemberService memberService;

    @Test
    @Transactional
    @DisplayName("리프레쉬 토큰 재발급 성공 테스트")
    void reissue() {
        LoginRequest loginRequest = new LoginRequest("asdf3@naver.com", "asdf3");
        LoginResponse login = authService.login(loginRequest);

        boolean b = redisService.hasKey(login.getEmail());
        assertTrue(b, "Redis에 Refresh Token이 저장되어야 합니다.");

        ReissueRequest reissueRequest = new ReissueRequest(redisService.getRefreshToken(login.getEmail()));
        ReissueResponse reissue = authService.reissue(reissueRequest.refreshToken());

        log.info("ReissueResponse: {}", reissue);
    }

    @Test
    @Transactional
    void 이메일_인증후_회원가입_성공() {
        // given
        String email = "test1234@example.com";
        String password = "securePassword123";

        String nickname = "tester";
        String name = "홍길동";

        redisService.markEmailAsVerified(email);

        MemberDtos.Response response = memberService.create(MemberDtos.CreateRequest.builder()
                .email(email)
                .password(password)
                .nickname(nickname)
                .confirmPassword(password)
                .build());

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();    // 실제 DB에 저장된 멤버 ID 반환되었는지
    }

    @Test
    @Transactional
    void 이메일_인증안된_상태에서_회원가입_실패() {
        // given
        String email = "failtest@example.com";
        String password = "fail1234";
        String nickname = "noverify";
        String name = "이탈자";

        // Redis에 인증 여부 설정하지 않음
        MemberDtos.CreateRequest request = MemberDtos.CreateRequest.builder()
                .email(email)
                .password(password)
                .confirmPassword(password)
                .nickname(nickname)
                .build();

        // when & then
        assertThatThrownBy(() -> memberService.create(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이메일 인증이 완료되지 않았습니다.");
    }
}