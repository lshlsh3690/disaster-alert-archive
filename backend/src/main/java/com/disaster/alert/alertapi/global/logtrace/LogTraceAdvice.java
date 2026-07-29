package com.disaster.alert.alertapi.global.logtrace;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import java.util.Arrays;
import java.util.stream.Collectors;

// LogTraceConfig의 포인트컷에 걸린 모든 Controller/Service/Repository 메서드 호출을 감싸
// begin -> proceed -> end(정상)/exception(예외) 순으로 LogTrace를 호출한다.
//
// risk/클러스터링/auth 도메인은 여기서 통째로 억제한다. 처음엔 포인트컷 표현식에
// !within(...)/!cflow(...)로 걸러보려 했는데, Spring AOP는 execution/within/args 등
// 일부 AspectJ 지시자만 지원하고 cflow()는 지원하지 않는다(Spring 공식 문서에 명시된
// 제약 — 실제로 넣어보니 포인트컷 전체가 조용히 아무 것도 안 걸리는 것으로 확인됨).
// within()은 지원되지만 "그 메서드 자신이 그 패키지에 있는지"만 보기 때문에, 예를 들어
// AuthService.reissue()를 within()으로 뺐어도 그 안에서 memberRepository.findByEmail()처럼
// 다른 패키지 Repository를 호출하면 그건 별도 join point라 다시 걸린다(리뷰로 지적받음).
// 그래서 이 클래스가 직접 ThreadLocal 깊이 카운터로 "지금 억제 대상 안에 들어와 있는지"를
// 추적해, 억제 대상에 처음 들어온 뒤로는 그 안에서 무엇을 호출하든(패키지 무관) 전부
// LogTrace를 완전히 건너뛴다 — 포인트컷은 관여하지 않고 어드바이스 로직만으로 처리.
public class LogTraceAdvice implements MethodInterceptor {

    private final LogTrace logTrace;
    // 0이면 억제 대상 밖, 1 이상이면 억제 대상(또는 그 안에서 중첩 호출된 무언가) 안.
    private final ThreadLocal<Integer> suppressedDepth = ThreadLocal.withInitial(() -> 0);

    public LogTraceAdvice(LogTrace logTrace) {
        this.logTrace = logTrace;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        boolean entersSuppressedDomain = isSuppressedDomain(invocation.getMethod().getDeclaringClass());
        boolean alreadySuppressed = suppressedDepth.get() > 0;

        if (entersSuppressedDomain || alreadySuppressed) {
            suppressedDepth.set(suppressedDepth.get() + 1);
            try {
                return invocation.proceed();
            } finally {
                suppressedDepth.set(suppressedDepth.get() - 1);
            }
        }

        TraceStatus status = null;
        try {
            // 인자를 같이 찍어야 동시에 여러 요청이 섞여도(트랜잭션ID로 구분은 되지만) 어떤
            // 값으로 호출됐는지 바로 보인다. 복잡한 객체는 toString()이 없으면 ClassName@hash로
            // 찍히는데, 그 정도는 감수 — 민감정보(비밀번호/토큰 등)를 원시 String으로 받는
            // 메서드가 있는 도메인(auth)은 통째로 억제 대상에 넣어뒀다.
            String args = Arrays.stream(invocation.getArguments())
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
            String message = invocation.getMethod().getDeclaringClass().getSimpleName()
                    + "." + invocation.getMethod().getName() + "(" + args + ")";
            status = logTrace.begin(message);
            Object result = invocation.proceed();
            logTrace.end(status);
            return result;
        } catch (Exception e) {
            if (status != null) {
                logTrace.exception(status, e);
            }
            throw e;
        }
    }

    // risk 도메인 전체, event의 클러스터링 관련 서비스, auth 도메인 전체를 억제한다.
    // 이유는 LogTraceConfig 주석 참고.
    private static boolean isSuppressedDomain(Class<?> declaringClass) {
        String name = declaringClass.getName();
        return name.startsWith("com.disaster.alert.alertapi.domain.risk.")
                || name.equals("com.disaster.alert.alertapi.domain.event.service.EventClusteringService")
                || name.equals("com.disaster.alert.alertapi.domain.event.service.EventCrossRegionService")
                || name.equals("com.disaster.alert.alertapi.domain.event.service.EventLLMDecisionService")
                || name.startsWith("com.disaster.alert.alertapi.domain.auth.");
    }
}
