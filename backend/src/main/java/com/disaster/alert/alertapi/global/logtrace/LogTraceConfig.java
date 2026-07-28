package com.disaster.alert.alertapi.global.logtrace;

import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Controller/Service 계층 호출을 자동으로 들여쓰기 로깅한다(인프런 김영한 "스프링 핵심 원리 -
// 고급편" LogTrace 패턴). Repository는 대부분 Spring Data JPA 인터페이스 프록시라 클래스 기반
// execution 포인트컷으로 걸기 애매해서 제외했다 — QueryDSL 커스텀 구현체(*RepositoryImpl)까지
// 추적하고 싶으면 아래 expression에 execution(* ..*RepositoryImpl.*(..))만 추가하면 된다.
@Configuration
public class LogTraceConfig {

    @Bean
    public LogTrace logTrace() {
        return new ThreadLocalLogTrace();
    }

    @Bean
    public Advisor logTraceAdvisor(LogTrace logTrace) {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression(
                "execution(* com.disaster.alert.alertapi..*Controller.*(..)) "
                        + "|| execution(* com.disaster.alert.alertapi..*Service.*(..))"
        );
        return new DefaultPointcutAdvisor(pointcut, new LogTraceAdvice(logTrace));
    }
}
