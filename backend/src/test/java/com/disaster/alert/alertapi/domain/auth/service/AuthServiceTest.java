package com.disaster.alert.alertapi.domain.auth.service;

import com.disaster.alert.alertapi.domain.auth.dto.ReissueRequest;
import com.disaster.alert.alertapi.domain.auth.dto.ReissueResponse;
import com.disaster.alert.alertapi.domain.member.dto.LoginRequest;
import com.disaster.alert.alertapi.domain.member.dto.LoginResponse;
import com.disaster.alert.alertapi.domain.member.dto.SignUpRequest;
import com.disaster.alert.alertapi.domain.member.dto.SignUpResponse;
import com.disaster.alert.alertapi.global.redis.RedisService;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Slf4j
@ActiveProfiles("test")
class AuthServiceTest {
    @Autowired
    private AuthService authService;

    @Autowired
    private RedisService redisService;

    @BeforeAll
    static void loadEnv() {
        Dotenv dotenv = Dotenv.configure()
                .filename(".env.test") // 해당 이름으로 명시
                .ignoreIfMissing()     // 없으면 무시
                .load();

        // 필요한 항목들 주입
        System.setProperty("DB_HOST", dotenv.get("DB_HOST"));
        System.setProperty("DB_PORT", dotenv.get("DB_PORT"));
        System.setProperty("POSTGRES_DB", dotenv.get("POSTGRES_DB"));
        System.setProperty("POSTGRES_USER", dotenv.get("POSTGRES_USER"));
        System.setProperty("POSTGRES_PASSWORD", dotenv.get("POSTGRES_PASSWORD"));
        System.setProperty("REDIS_HOST", dotenv.get("REDIS_HOST"));
        System.setProperty("REDIS_PORT", dotenv.get("REDIS_PORT"));
        System.setProperty("GMAIL_USERNAME", dotenv.get("GMAIL_USERNAME"));
        System.setProperty("GMAIL_PASSWORD", dotenv.get("GMAIL_PASSWORD"));
        System.setProperty("JWT_SECRET", dotenv.get("JWT_SECRET"));
        System.setProperty("DISASTER_ALERT_SERVICE_KEY", dotenv.get("DISASTER_ALERT_SERVICE_KEY"));
    }

    @Test
    @Transactional
    @DisplayName("리프레쉬 토큰 재발급 성공 테스트")
    void reissue() {
        LoginRequest loginRequest = new LoginRequest("asdf3@naver.com", "asdf3");
        LoginResponse login = authService.login(loginRequest);

        boolean b = redisService.hasKey(login.getEmail());
        assertTrue(b, "Redis에 Refresh Token이 저장되어야 합니다.");

        ReissueRequest reissueRequest = new ReissueRequest(redisService.getRefreshToken(login.getEmail()));
        ReissueResponse reissue = authService.reissue(reissueRequest);

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

        SignUpResponse response = authService.signUp(SignUpRequest.builder()
                .email(email)
                .password(password)
                .nickname(nickname)
                .confirmPassword(password)
                .build());

        // then
        assertThat(response).isNotNull();
        assertThat(response.getMessage()).contains("회원가입이 완료되었습니다."); // 메시지 내용 확인 (메시지 내용이 실제로 "회원가입 성공" 같은 거라면)
        assertThat(response.getMemberId()).isNotNull();    // 실제 DB에 저장된 멤버 ID 반환되었는지
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
        SignUpRequest request = SignUpRequest.builder()
                .email(email)
                .password(password)
                .confirmPassword(password)
                .nickname(nickname)
                .build();

        // when & then
        assertThatThrownBy(() -> authService.signUp(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이메일 인증이 완료되지 않았습니다.");
    }
}