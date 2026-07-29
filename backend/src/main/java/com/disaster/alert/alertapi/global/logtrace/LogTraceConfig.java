package com.disaster.alert.alertapi.global.logtrace;

import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Controller/Service/Repository 계층 호출을 자동으로 들여쓰기 로깅한다(인프런 김영한
// "스프링 핵심 원리 - 고급편" LogTrace 패턴). Repository는 Spring Data JPA 인터페이스
// 프록시라도 execution이 인터페이스 선언 메서드 기준으로 정상 매칭된다.
//
// risk 도메인 전체와 event의 클러스터링 관련 서비스는 뺐다 — 이쪽은 원래 설계상 지역·
// 후보 단위로 반복 호출되는 게 정상 동작이라(예: RiskMaintenanceService가 활성 시군구
// 수백 개마다 RiskCalculationService.recomputeRegionSource()를, 그 안에서
// RegionRiskIndexRepository.upsertEffective()를 반복 호출), 계층 종류와 무관하게
// 계층형 로그를 붙이면 호출 1회당 로그 수백~수천 줄이 쏟아지는 걸 운영 로그에서 직접
// 확인함. 이 서비스들은 기존에도 실패 지점마다 log.error(msg, e)로 직접 잡아서 남기고
// 있어서(RiskMaintenanceService의 catch(Exception e) 등), LogTrace 없이도 예외는 그대로
// 일반 로그로 보이고 Sentry 로그백 연동도 유지된다.
//
// 이 필터에 안 걸리는 다른 반복 호출 패턴(예: DisasterAlertService.saveData()의 중복 SN
// 재시도 저장 루프 — disasterAlertRepository.save(alert)를 페이지당 최대 1000번까지 호출
// 가능)도 이론상 남아있다. 지금까지는 실제 운영 로그에서 문제될 만큼 자주 걸린 적이
// 없어서 남겨뒀지만, 비슷하게 로그가 몰리는 게 보이면 이 방식대로 좁혀서 제외할 것.
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
                        + "|| execution(* com.disaster.alert.alertapi..*Service.*(..)) "
                        + "|| execution(* com.disaster.alert.alertapi..*Repository.*(..))) "
                        + "&& !within(com.disaster.alert.alertapi.domain.risk..*) "
                        + "&& !within(com.disaster.alert.alertapi.domain.event.service.EventClusteringService) "
                        + "&& !within(com.disaster.alert.alertapi.domain.event.service.EventCrossRegionService) "
                        + "&& !within(com.disaster.alert.alertapi.domain.event.service.EventLLMDecisionService)"
        );
        return new DefaultPointcutAdvisor(pointcut, new LogTraceAdvice(logTrace));
    }
}
