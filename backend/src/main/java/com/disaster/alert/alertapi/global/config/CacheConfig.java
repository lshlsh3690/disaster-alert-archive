package com.disaster.alert.alertapi.global.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@EnableCaching
@Configuration
public class CacheConfig {
    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory redisConnectionFactory) {
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

//        cacheConfigurations.put("nicknameCheck",
//                RedisCacheConfiguration.defaultCacheConfig()
//                        .entryTtl(Duration.ofMinutes(5))  // 닉네임 캐시만 5분
//                        .disableCachingNullValues()
//        );

        // JDK 직렬화 대신 JSON 직렬화 사용.
        // /stats 캐시 대상 DTO(QueryDSL Projections.constructor 반환용, @Getter만 있고
        // Serializable 미구현)는 기본 JdkSerializationRedisSerializer로 캐싱 불가.
        // GenericJackson2JsonRedisSerializer는 필드 리플렉션 + "@class" 타입 정보 기록으로
        // getter/setter/기본생성자 없이도 역직렬화 가능.
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
