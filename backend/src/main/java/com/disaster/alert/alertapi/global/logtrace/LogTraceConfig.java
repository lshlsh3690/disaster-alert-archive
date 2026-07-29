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
// 포인트컷 자체엔 도메인 제외를 안 건다 — 처음엔 !within(...), 그다음 !cflow(...)로
// risk/클러스터링/auth 도메인을 걸러보려 했는데:
//   1) !within(SomeService)는 "그 메서드 자신이 그 클래스에 선언돼 있는지"만 보기 때문에,
//      그 안에서 다른 패키지의 Repository를 호출하면(예: AuthService.reissue()가
//      memberRepository.findByEmail() 호출) 그 Repository 호출은 별도 join point라
//      제외가 안 먹힘.
//   2) 그래서 "호출 흐름 전체를 제외"하는 !cflow(...)로 바꿨는데, Spring AOP는
//      execution/within/args 등 일부 AspectJ 지시자만 지원하고 cflow()는 지원하지
//      않는다(Spring 공식 문서에 명시된 제약). 실제로 붙여보니 포인트컷 전체가 조용히
//      아무 것도 안 걸리는 것으로 확인됨(정상 Controller/Service까지 트레이스가 하나도
//      안 찍힘) — 둘 다 CodeRabbit 리뷰/직접 검증으로 잡아낸 문제.
// 그래서 도메인 제외는 포인트컷이 아니라 LogTraceAdvice 안에서 ThreadLocal 깊이
// 카운터로 직접 처리한다(LogTraceAdvice 주석 참고) — Spring AOP 제약과 무관하게 정확히
// "그 도메인 진입 후로는 무엇을 호출하든" 억제할 수 있다.
//
// 그 억제 목록에 안 걸리는 다른 반복 호출 패턴(예: DisasterAlertService.saveData()의
// 중복 SN 재시도 저장 루프 — disasterAlertRepository.save(alert)를 페이지당 최대
// 1000번까지 호출 가능)도 이론상 남아있다. 지금까지는 실제 운영 로그에서 문제될 만큼
// 자주 걸린 적이 없어서 남겨뒀지만, 비슷하게 로그가 몰리는 게 보이면
// LogTraceAdvice.isSuppressedDomain()에 좁혀서 추가할 것.
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
