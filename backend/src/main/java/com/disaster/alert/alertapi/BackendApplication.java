package com.disaster.alert.alertapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;

@OpenAPIDefinition(
        info = @Info(
                title = "재난 문자 아카이브 API",
                description = "재난 안전 문자 수집 및 사용자 맞춤형 알림 서비스 API",
                version = "v1"
        ),
        servers = {
                @Server(url = "https://api.disaster-alert-archive.co.kr", description = "Production"),
                @Server(url = "http://localhost:8080", description = "Local")
        }
)
@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing
@EnableAsync
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

}
