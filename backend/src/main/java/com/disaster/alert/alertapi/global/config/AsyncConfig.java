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
}
