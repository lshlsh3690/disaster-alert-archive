package com.disaster.alert.alertapi.global.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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
}
