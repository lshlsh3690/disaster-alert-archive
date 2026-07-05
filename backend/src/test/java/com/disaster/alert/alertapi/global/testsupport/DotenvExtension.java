package com.disaster.alert.alertapi.global.testsupport;

import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * backend/.env.test 의 모든 항목을 System property 로 주입한다.
 *
 * <p>테스트 클래스마다 {@code @BeforeAll} + Dotenv 로딩 코드를 반복 작성하지 않도록 추출한 확장.
 * {@link IntegrationTest} 를 통해 사용한다.
 */
public class DotenvExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        Dotenv dotenv = Dotenv.configure()
                .filename(".env.test")
                .ignoreIfMissing()
                .load();

        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
    }
}
