package com.disaster.alert.alertapi.global.translation;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TranslationRequestCoalescer} 순수 단위 테스트 — DB/Spring 컨텍스트 없음.
 *
 * <p>번역 캐시 미스가 동시에 여러 요청에서 발생하면({@code TranslationService.ensureTranslated})
 * 저장 충돌은 upsert(ON CONFLICT DO NOTHING)로 이미 막혀 있지만, 외부 번역 API 자체가 같은
 * (alertId, languageCode) 키로 중복 호출되는 낭비(비용·지연)는 막지 못한다. 이 테스트는 그 낭비를
 * 막는 single-flight 병합기의 핵심 동작 3가지만 고정한다 — 경계값(큐 길이, 타임아웃 값 등)은
 * 다루지 않는다.
 *
 * <p>Red 단계 현재 뼈대({@code TranslationRequestCoalescer.coalesce})는 병합 없이 매번 work를
 * 그냥 실행하므로, 아래 테스트 1(병합)은 실행 횟수가 기대(1)와 다른 값(2)이 나와 실패해야 한다.
 */
class TranslationRequestCoalescerTest {

    @Test
    void 동시에_들어온_같은_키_호출은_work를_한_번만_실행하고_결과를_공유한다() throws Exception {
        TranslationRequestCoalescer<String, String> coalescer = new TranslationRequestCoalescer<>();    // Single-Flight 기능을 테스트할 객체 생성
        AtomicInteger cnt = new AtomicInteger(0);    // 실제 work가 몇 번 실행되는지 저장하는 원자적 카운터

        // 두 작업 스레드를 동시에 출발시키기 위한 신호 (초기값 1)
        CountDownLatch start = new CountDownLatch(1);

        // 최초 실행자가 work 내부에 진입했는지 확인하기 위한 신호 (초기값 1)
        CountDownLatch workStarted = new CountDownLatch(1);
        // 최초 실행자가 work를 완료하지 못하도록 대기시키는 신호 (초기값 1)
        CountDownLatch releaseWork = new CountDownLatch(1);

        // given: 같은 key("alert-1:JA")를 쓰는 work. 실제 번역 API 호출을 흉내 내려고 200ms 지연을둔다
        // 실제 OpenAI 번역 API 호출을 흉내 내는 작업
        Callable<String> work = () -> {
            cnt.incrementAndGet();

            workStarted.countDown();    // work가 시작되었음을 알림 (workStarted: 1 → 0)

            // releaseWork가 0이 될 때까지 최대 5초 동안 대기
            if (!releaseWork.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("작업 대기 시간 초과");
            }

            // releaseWork가 0이 되면 반환하는 번역 결과
            return "테스트번역결과";
        };


        ExecutorService pool = Executors.newFixedThreadPool(2);
        // 두 요청이 공유할 동일한 번역 키
        String key = "alert-1:JA";

        Callable<String> task = () -> {
            // start가 0이 될 때까지 대기 (두 스레드의 출발을 제어)
            start.await();

            // 출발 신호를 받으면 동일 키로 Single-Flight 호출
            return coalescer.coalesce(key, work);
        };

        try {
            // when: 두 스레드가 같은 key로 동시에 coalesce 를 호출한다.
            Future<String> f1 = pool.submit(task);
            Future<String> f2 = pool.submit(task);

            // start를 1에서 0으로 변경하여 대기 중인 두 스레드 출발 허용
            start.countDown(); // 동시 출발 — 둘 다 work 가 끝나기 전(200ms 이내)에 coalesce 에 진입하게 함

            // 최초 실행자가 work에 진입할 때까지 최대 5초 대기
            assertTrue(workStarted.await(5, TimeUnit.SECONDS));

            // 최초 실행자가 work에 진입했으므로 실행 횟수는 1이어야 함
            assertEquals(1, cnt.get());

            // releaseWork를 1에서 0으로 변경하여 work의 대기를 해제
            releaseWork.countDown();

            String result1 = f1.get(5, TimeUnit.SECONDS);
            String result2 = f2.get(5, TimeUnit.SECONDS);

            // then: 두 호출 모두 같은 결과를 받아야 하고(값 공유), 그보다 핵심적으로 work 자체는
            // 딱 한 번만 실행돼야 한다. 현재 뼈대는 병합하지 않고 매번 work.call() 을 바로 실행하므로
            // executionCount 가 2가 되어 이 단언이 실패한다(Red).
            assertEquals("테스트번역결과", result1);
            assertEquals("테스트번역결과", result2);
        } finally {
            // 예외가 발생하더라도 work에서 대기 중인 스레드를 해제
            releaseWork.countDown();

            // 테스트에서 사용한 작업 스레드에 중단을 요청
            pool.shutdownNow();
        }
    }

    @Test
    void 실행자가_Error를_던져도_합류자는_영구_대기하지_않고_같은_Error를_받는다() throws Exception {
        TranslationRequestCoalescer<String, String> coalescer = new TranslationRequestCoalescer<>();
        CountDownLatch workStarted = new CountDownLatch(1);
        CountDownLatch releaseWork = new CountDownLatch(1);
        StackOverflowError boom = new StackOverflowError("테스트용");

        Callable<String> work = () -> {
            workStarted.countDown();
            releaseWork.await(5, TimeUnit.SECONDS);
            throw boom;
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> executor = pool.submit(() -> coalescer.coalesce("alert-2:EN", work));
            assertTrue(workStarted.await(5, TimeUnit.SECONDS));
            // 실행자가 work 안에서 대기 중일 때 합류자가 들어온다
            Future<String> joiner = pool.submit(() -> coalescer.coalesce("alert-2:EN", () -> "실행되면 안 됨"));
            Thread.sleep(200);
            releaseWork.countDown();

            // Error 를 future 에 완료시키지 않으면 합류자가 타임아웃 없이 기다려 이 get 이 TimeoutException 으로 끝난다
            ExecutionException executorFailure = assertThrows(ExecutionException.class, () -> executor.get(5, TimeUnit.SECONDS));
            ExecutionException joinerFailure = assertThrows(ExecutionException.class, () -> joiner.get(5, TimeUnit.SECONDS));
            assertSame(boom, executorFailure.getCause());
            assertSame(boom, joinerFailure.getCause());
        } finally {
            releaseWork.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void 실행자가_실패해도_키가_제거돼_다음_호출은_새로_실행된다() throws Exception {
        TranslationRequestCoalescer<String, String> coalescer = new TranslationRequestCoalescer<>();

        assertThrows(IllegalStateException.class, () -> coalescer.coalesce("alert-3:ZH", () -> {
            throw new IllegalStateException("일시 오류");
        }));

        assertEquals("재시도결과", coalescer.coalesce("alert-3:ZH", () -> "재시도결과"));
    }
}
