package com.disaster.alert.alertapi.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import com.disaster.alert.alertapi.global.translation.TranslationProperties;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableConfigurationProperties(TranslationProperties.class)
public class AsyncConfig {

    @Bean(name = "translationExecutor")
    public Executor translationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("translation-");
        executor.initialize();
        return executor;
    }

    /**
     * 날씨 백필 비동기 실행 전용 풀.
     * 백필러는 단일 long-running 작업이므로 동시 실행을 컨트롤러 측에서 막고 풀 크기는 1.
     */
    @Bean(name = "backfillExecutor")
    public Executor backfillExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("weather-backfill-");
        executor.initialize();
        return executor;
    }
}
