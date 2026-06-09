package com.disaster.alert.alertapi.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import com.disaster.alert.alertapi.global.translation.TranslationProperties;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableConfigurationProperties(TranslationProperties.class)
public class AsyncConfig {

    // riskTaskExecutor가 추가되어 Executor 빈이 2개가 됨.
    // 이름 없는 기존 @Async들이 SimpleAsyncTaskExecutor로 조용히 폴백되어 무한 스레드를 생성하는 버그를 막기 위해
    // 기존에 단일로 쓰이던 translationExecutor를 기본(Primary) 풀로 명시적 지정함.
    @Primary
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

    @Bean(name = "riskTaskExecutor")
    public Executor riskTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("risk-");
        executor.initialize();
        return executor;
    }
}
