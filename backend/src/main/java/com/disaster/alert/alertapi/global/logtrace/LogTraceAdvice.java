package com.disaster.alert.alertapi.global.logtrace;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import java.util.Arrays;
import java.util.stream.Collectors;

// LogTraceConfig의 포인트컷에 걸린 모든 Controller/Service 메서드 호출을 감싸
// begin -> proceed -> end(정상)/exception(예외) 순으로 LogTrace를 호출한다.
public class LogTraceAdvice implements MethodInterceptor {

    private final LogTrace logTrace;

    public LogTraceAdvice(LogTrace logTrace) {
        this.logTrace = logTrace;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        TraceStatus status = null;
        try {
            // 인자를 같이 찍어야 동시에 여러 요청이 섞여도(트랜잭션ID로 구분은 되지만) 어떤
            // 값으로 호출됐는지 바로 보인다. 복잡한 객체는 toString()이 없으면 ClassName@hash로
            // 찍히는데, 그 정도는 감수 — 민감정보(비밀번호 등)를 받는 메서드가 이 포인트컷에
            // 걸리면 별도로 제외해야 함(LogTraceConfig 참고).
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
}
