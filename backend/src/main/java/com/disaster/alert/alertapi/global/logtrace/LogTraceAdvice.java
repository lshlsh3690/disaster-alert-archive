package com.disaster.alert.alertapi.global.logtrace;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

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
            String message = invocation.getMethod().getDeclaringClass().getSimpleName()
                    + "." + invocation.getMethod().getName() + "()";
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
