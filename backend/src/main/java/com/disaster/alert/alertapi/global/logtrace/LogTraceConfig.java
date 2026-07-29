package com.disaster.alert.alertapi.global.logtrace;

import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Controller/Service 계층 호출을 자동으로 들여쓰기 로깅한다(인프런 김영한
// "스프링 핵심 원리 - 고급편" LogTrace 패턴).
//
// Repository는 의도적으로 뺐다 — 처음엔 execution 포인트컷에 *Repository도 넣어봤는데
// (Spring Data JPA 인터페이스 프록시라도 execution이 인터페이스 선언 메서드 기준으로
// 정상 매칭됨), RiskCalculationService.propagateEffective()처럼 전체 시군구(250개+) 개수만큼
// repository 메서드를 반복 호출하는 패턴이 이 프로젝트에 실제로 있어서, 그런 호출 하나가
// 로그 수백~수천 줄을 쏟아내는 걸 운영 로그에서 직접 확인함(재난문자 저장 재시도 루프 등
// 비슷한 반복 패턴이 더 있음). Controller/Service는 요청당 1회라 안전하지만 Repository는
// N+1/배치 호출이 흔해서 블랭킷으로 걸면 위험 — 특정 리포지토리만 추적하고 싶으면
// execution(* ..SomeSpecificRepositoryImpl.*(..)) 처럼 좁게 추가할 것.
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
