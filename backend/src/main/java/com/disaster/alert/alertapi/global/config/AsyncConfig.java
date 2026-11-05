package com.disaster.alert.alertapi.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    // 이름 없는 @Async 가 SimpleAsyncTaskExecutor 로 조용히 폴백해 무한 스레드를 만드는 버그를 막으려고
    // translationExecutor 를 기본(Primary) 풀로 명시한다. 한때 riskTaskExecutor 가 함께 있어 빈이 2개였고
    // 그래서 Primary 지정이 필요했는데, 위험도 기능 삭제(2026-11-05)로 그 빈은 사라졌다. 빈이 하나뿐이어도
    // Primary 는 무해하고, 두 번째 풀이 다시 생겼을 때 같은 버그가 재발하지 않게 남겨 둔다.
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
}
