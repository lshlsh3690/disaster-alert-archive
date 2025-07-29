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
        return redisTemplate.hasKey(email);
    }

    public void deleteRefreshToken(String email) {
        redisTemplate.delete(email);
    }
}
