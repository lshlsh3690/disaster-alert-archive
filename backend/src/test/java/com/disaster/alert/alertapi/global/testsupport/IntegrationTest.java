package com.disaster.alert.alertapi.global.testsupport;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @SpringBootTest @ActiveProfiles("test")} 에 더해, backend/.env.test 값을
 * 시스템 프로퍼티로 주입하는 {@link DotenvExtension} 까지 묶은 통합 테스트용 애노테이션.
 *
 * <p>실제(테스트용) DB/Redis 등에 붙는 {@code @SpringBootTest} 클래스에 이 애노테이션 하나만 붙이면 된다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(DotenvExtension.class)
public @interface IntegrationTest {
}
