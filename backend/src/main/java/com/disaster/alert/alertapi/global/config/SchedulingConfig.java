package com.disaster.alert.alertapi.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

// 스케줄러(@Scheduled 전부)는 운영(prod) 프로필에서만 켠다.
// SPRING_PROFILES_ACTIVE=prod 는 docker-compose.prod.yml 에서만 설정되므로, 로컬
// ./gradlew bootRun(프로필 없음)이나 @ActiveProfiles("test") 통합 테스트에서는 자동으로 꺼진다.
//
// 원래 BackendApplication에 @EnableScheduling이 있었는데, 로컬 bootRun이 .env.dev로
// 실제 운영 RDS에 붙은 채로 뜨면 재난문자 수집·번역·클러스터링 스케줄러가 진짜로 돌아버려서
// (DeepL 쿼터 소모, OpenAI 임베딩 호출 등 실제 부수효과 발생 — 로컬 검증 중 실제로 겪음)
// 여기로 분리해 운영에서만 켜지게 했다.
@Configuration
@Profile("prod")
@EnableScheduling
public class SchedulingConfig {
}
