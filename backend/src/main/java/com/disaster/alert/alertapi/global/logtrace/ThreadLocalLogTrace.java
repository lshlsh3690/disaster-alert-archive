package com.disaster.alert.alertapi.global.logtrace;

import lombok.extern.slf4j.Slf4j;

// Controller -> Service 호출을 [트랜잭션ID] |   |-->method() 형태로 들여쓰기 출력한다.
// ThreadLocal 로 요청(스레드)별 깊이를 추적 — end/exception에서 반드시 level을 되돌리거나
// remove() 해야 스레드 풀 재사용 시 이전 요청의 level이 새 요청에 새어 들어가지 않는다.
//
// begin/end/exception 전부 INFO 레벨로 고정한다. 여기서 exception()을 ERROR로 찍으면
// 정상적인 4xx 비즈니스 예외(CustomException 등)까지 Sentry(logging.minimum-event-level=error)로
// 전송돼 노이즈가 된다 — 실제 장애 여부 판단은 GlobalExceptionHandler/각 스케줄러의 log.error
// 몫이고, 이 클래스는 순수하게 호출 흐름과 소요 시간을 보여주는 용도로만 쓴다.
@Slf4j
public class ThreadLocalLogTrace implements LogTrace {

    private static final String START_PREFIX = "-->";
    private static final String COMPLETE_PREFIX = "<--";
    private static final String EX_PREFIX = "<X-";

    private final ThreadLocal<TraceId> traceIdHolder = new ThreadLocal<>();

    @Override
    public TraceStatus begin(String message) {
        syncTraceId();
        TraceId traceId = traceIdHolder.get();
        long startTimeMs = System.currentTimeMillis();
        log.info("[{}] {}{}", traceId.getId(), addSpace(START_PREFIX, traceId.getLevel()), message);
        return new TraceStatus(traceId, startTimeMs, message);
    }

    @Override
    public void end(TraceStatus status) {
        complete(status, null);
    }

    @Override
    public void exception(TraceStatus status, Exception e) {
        complete(status, e);
    }

    private void complete(TraceStatus status, Exception e) {
        long resultTimeMs = System.currentTimeMillis() - status.getStartTimeMs();
        TraceId traceId = status.getTraceId();
        if (e == null) {
            log.info("[{}] {}{} time={}ms", traceId.getId(), addSpace(COMPLETE_PREFIX, traceId.getLevel()), status.getMessage(), resultTimeMs);
        } else {
            log.info("[{}] {}{} time={}ms ex={}", traceId.getId(), addSpace(EX_PREFIX, traceId.getLevel()), status.getMessage(), resultTimeMs, e.toString());
        }
        releaseTraceId();
    }

    private void syncTraceId() {
        TraceId traceId = traceIdHolder.get();
        traceIdHolder.set(traceId == null ? new TraceId() : traceId.createNextId());
    }

    private void releaseTraceId() {
        TraceId traceId = traceIdHolder.get();
        if (traceId.isFirstLevel()) {
            traceIdHolder.remove();
        } else {
            traceIdHolder.set(traceId.createPreviousId());
        }
    }

    private static String addSpace(String prefix, int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append(i == level - 1 ? "|" + prefix : "|   ");
        }
        return sb.toString();
    }
}
