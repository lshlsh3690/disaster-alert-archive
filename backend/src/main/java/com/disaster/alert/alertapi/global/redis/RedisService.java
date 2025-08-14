package com.disaster.alert.alertapi.global.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisService {
    private final StringRedisTemplate redisTemplate;

    public void saveRefreshToken(String email, String refreshToken, long seconds) {
        redisTemplate.opsForValue().set(email, refreshToken, seconds, TimeUnit.SECONDS);
    }

    public String getRefreshToken(String email) {
        return redisTemplate.opsForValue().get(email);
    }

    public boolean hasKey(String email) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(email));
    }

    public void deleteRefreshToken(String email) {
        redisTemplate.delete(email);
    }

    public void saveBlackListToken(String token, long expirationMillis) {
        redisTemplate.opsForValue().set("BL_" + token, "logout", expirationMillis, TimeUnit.MILLISECONDS);
    }

    public boolean isBlackListToken(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("BL_" + token));
    }

    public void setEmailVerificationCode(String email, String code, Duration duration) {
        redisTemplate.opsForValue().set("EV"+email, code, duration);
    }

    public String getEmailVerificationCode(String email) {
        return redisTemplate.opsForValue().get("EV"+email);
    }

    public void deleteEmailVerificationCode(String email) {
        redisTemplate.delete("EV"+email);
    }

    public void markEmailAsVerified(String email) {
        String key = getEmailVerifiedKey(email);
        redisTemplate.opsForValue().set(key, "true", Duration.ofMinutes(3)); // 인증 후 10분간 유효
    }

    public boolean isEmailVerified(String email) {
        String key = getEmailVerifiedKey(email);
        return "true".equals(redisTemplate.opsForValue().get(key));
    }

    private String getEmailVerifiedKey(String email) {
        return "VERIFIED_EMAIL_" + email;
    }
}
