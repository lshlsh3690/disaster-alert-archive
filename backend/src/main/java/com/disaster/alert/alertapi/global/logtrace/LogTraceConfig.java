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
// 로그 수백~수천 줄을 쏟아내는 걸 운영 로그에서 직접 확인함. Controller/Service는 요청당
// 1회라 안전하지만 Repository는 N+1/배치 호출이 흔해서 블랭킷으로 걸면 위험 — 특정
// 리포지토리만 추적하고 싶으면 execution(* ..SomeSpecificRepositoryImpl.*(..)) 처럼
// 좁게 추가할 것.
//
// risk 도메인 전체와 event의 클러스터링 관련 서비스도 뺐다 — 이쪽은 원래 설계상
// 지역/후보 단위로 반복 호출되는 게 정상 동작이라(예: RiskMaintenanceService가 활성
// 시군구 수백 개마다 RiskCalculationService.recomputeRegionSource()를 호출), Service
// 계층이라도 계층형 로그를 붙이면 똑같이 폭증한다. 이 서비스들은 기존에도 실패 지점마다
// log.error(msg, e)로 직접 잡아서 남기고 있어서(RiskMaintenanceService의
// catch(Exception e) 등), LogTrace 없이도 예외는 그대로 일반 로그로 보인다.
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
                "(execution(* com.disaster.alert.alertapi..*Controller.*(..)) "
                        + "|| execution(* com.disaster.alert.alertapi..*Service.*(..))) "
                        + "&& !within(com.disaster.alert.alertapi.domain.risk..*) "
                        + "&& !within(com.disaster.alert.alertapi.domain.event.service.EventClusteringService) "
                        + "&& !within(com.disaster.alert.alertapi.domain.event.service.EventCrossRegionService) "
                        + "&& !within(com.disaster.alert.alertapi.domain.event.service.EventLLMDecisionService)"
        );
        return new DefaultPointcutAdvisor(pointcut, new LogTraceAdvice(logTrace));
    }
}
