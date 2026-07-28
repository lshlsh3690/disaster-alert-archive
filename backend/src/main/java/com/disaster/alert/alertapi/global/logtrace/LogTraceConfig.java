package com.disaster.alert.alertapi.global.logtrace;

import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Controller/Service/Repository 계층 호출을 자동으로 들여쓰기 로깅한다(인프런 김영한
// "스프링 핵심 원리 - 고급편" LogTrace 패턴). Repository는 Spring Data JPA가 만드는
// 인터페이스 프록시라 execution 포인트컷이 인터페이스 선언 메서드(findById, save 등)
// 기준으로 매칭된다 — 클래스가 아니라 인터페이스 이름이 *Repository로 끝나는 경우 매칭.
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
                        + "|| execution(* com.disaster.alert.alertapi..*Service.*(..)) "
                        + "|| execution(* com.disaster.alert.alertapi..*Repository.*(..))"
        );
        return new DefaultPointcutAdvisor(pointcut, new LogTraceAdvice(logTrace));
    }
}
