package com.disaster.alert.alertapi.global.logtrace;

import lombok.extern.slf4j.Slf4j;

// Controller(level 0, 화살표 없음) -> Service(level 1, "-->") -> Repository(level 2, "---->")
// 순으로 깊이마다 화살표가 2칸씩 늘어나는 형태로 [트랜잭션ID] 를 붙여 로깅한다.
// 예) [ab12cd34] DisasterAlertController.getAlertDetail()
//     [ab12cd34] -->DisasterAlertService.getAlertDetail()
//     [ab12cd34] ---->DisasterAlertRepository.findById()
// ThreadLocal 로 요청(스레드)별 깊이를 추적 — end/exception에서 반드시 level을 되돌리거나
// remove() 해야 스레드 풀 재사용 시 이전 요청의 level이 새 요청에 새어 들어가지 않는다.
//
// begin/end/exception 전부 INFO 레벨로 고정한다. 여기서 exception()을 ERROR로 찍으면
// 정상적인 4xx 비즈니스 예외(CustomException 등)까지 Sentry(logging.minimum-event-level=error)로
// 전송돼 노이즈가 된다 — 실제 장애 여부 판단은 GlobalExceptionHandler/각 스케줄러의 log.error
// 몫이고, 이 클래스는 순수하게 호출 흐름과 소요 시간을 보여주는 용도로만 쓴다.
@Slf4j
public class ThreadLocalLogTrace implements LogTrace {

    private final ThreadLocal<TraceId> traceIdHolder = new ThreadLocal<>();

    @Override
    public TraceStatus begin(String message) {
        syncTraceId();
        TraceId traceId = traceIdHolder.get();
        long startTimeNanos = System.nanoTime();
        log.info("[{}] {}{}", traceId.getId(), startArrow(traceId.getLevel()), message);
        return new TraceStatus(traceId, startTimeNanos, message);
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
        long resultTimeMs = (System.nanoTime() - status.getStartTimeNanos()) / 1_000_000;
        TraceId traceId = status.getTraceId();
        int level = traceId.getLevel();
        if (e == null) {
            log.info("[{}] {}{} time={}ms", traceId.getId(), completeArrow(level), status.getMessage(), resultTimeMs);
        } else {
            log.info("[{}] {}{} time={}ms ex={}", traceId.getId(), exceptionArrow(level), status.getMessage(), resultTimeMs, e.toString());
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

    // level 0(Controller)은 화살표 없이 메서드명만 — 최상위 진입은 트랜잭션ID 자체가 표식.
    private static String startArrow(int level) {
        return level == 0 ? "" : "-".repeat(2 * level) + ">";
    }

    private static String completeArrow(int level) {
        return level == 0 ? "" : "<" + "-".repeat(2 * level);
    }

    private static String exceptionArrow(int level) {
        return level == 0 ? "" : "<X" + "-".repeat(2 * level - 1);
    }
}
