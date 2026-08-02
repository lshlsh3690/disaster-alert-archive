package com.disaster.alert.alertapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;

import java.util.TimeZone;

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
@EnableJpaAuditing
@EnableAsync
public class BackendApplication {

    public static void main(String[] args) {
        // JVM 기본 타임존이 KST가 아니면 LocalDateTime.now() 기반의 모든 생성/수정 시각
        // (JPA Auditing 등)이 실제 시각과 어긋난다. 컨테이너/환경변수에 의존하지 않도록
        // 애플리케이션 시작 시점에 명시적으로 고정한다.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        SpringApplication.run(BackendApplication.class, args);
    }

}
