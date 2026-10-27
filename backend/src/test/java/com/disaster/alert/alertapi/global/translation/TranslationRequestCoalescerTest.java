package com.disaster.alert.alertapi.global.translation;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        TranslationRequestCoalescer<String, String> coalescer = new TranslationRequestCoalescer<>();
        AtomicInteger executionCount = new AtomicInteger(0);

        // given: 같은 key("alert-1:JA")를 쓰는 work. 실제 번역 API 호출을 흉내 내려고 200ms 지연을
        // 둔다 — 지연이 없으면 두 스레드의 호출이 겹치기 전에 하나가 먼저 끝나버려 병합 여부를
        // 재현하지 못할 수 있다(기존 DisasterAlertServiceTranslationConcurrencyTest 와 같은 이유).
        Callable<String> work = () -> {
            executionCount.incrementAndGet();
            Thread.sleep(200);
            return "테스트번역결과";
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        String key = "alert-1:JA";

        Callable<String> task = () -> {
            start.await();
            return coalescer.coalesce(key, work);
        };

        // when: 두 스레드가 같은 key로 동시에 coalesce 를 호출한다.
        Future<String> f1 = pool.submit(task);
        Future<String> f2 = pool.submit(task);
        start.countDown(); // 동시 출발 — 둘 다 work 가 끝나기 전(200ms 이내)에 coalesce 에 진입하게 함

        try {
            String result1 = f1.get(5, TimeUnit.SECONDS);
            String result2 = f2.get(5, TimeUnit.SECONDS);

            // then: 두 호출 모두 같은 결과를 받아야 하고(값 공유), 그보다 핵심적으로 work 자체는
            // 딱 한 번만 실행돼야 한다. 현재 뼈대는 병합하지 않고 매번 work.call() 을 바로 실행하므로
            // executionCount 가 2가 되어 이 단언이 실패한다(Red).
            assertEquals("테스트번역결과", result1);
            assertEquals("테스트번역결과", result2);
            assertEquals(1, executionCount.get(),
                    "같은 키로 동시에 들어온 호출은 work를 한 번만 실행해야 한다");
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void 서로_다른_키는_서로를_블로킹하지_않고_병렬로_처리된다() throws Exception {
        TranslationRequestCoalescer<String, String> coalescer = new TranslationRequestCoalescer<>();

        // given: 서로 다른 key(A, B)에 대한 work 각각이, "시작할 때 자기 쪽 latch 를 내리고
        // 상대방 latch 가 짧은 시간 안에 내려가는지"를 확인한다. 상대방이 실제로 거의 동시에
        // 시작했다는 뜻이므로, 이 확인이 true 면 두 작업이 전역 lock 으로 직렬화되지 않았다는
        // 증거가 된다. 이 테스트는 "모든 키를 하나의 lock 으로 묶는" 잘못된 Green 구현을
        // 막기 위한 회귀 가드다 — 뼈대(병합 없음)에서도 통과할 수 있고, 그 자체는 문제가 아니다.
        CountDownLatch startedA = new CountDownLatch(1);
        CountDownLatch startedB = new CountDownLatch(1);
        AtomicInteger bothRanConcurrently = new AtomicInteger(0);

        Callable<String> workA = () -> {
            startedA.countDown();
            // B 도 거의 동시에 시작했는지 짧게 기다려 확인한다. 2초면 스케줄링 지연을 흡수하면서도
            // 실제 직렬화(순차 실행)라면 절대 안 걸리는 시간이다.
            if (startedB.await(2, TimeUnit.SECONDS)) {
                bothRanConcurrently.incrementAndGet();
            }
            return "A-done";
        };
        Callable<String> workB = () -> {
            startedB.countDown();
            if (startedA.await(2, TimeUnit.SECONDS)) {
                bothRanConcurrently.incrementAndGet();
            }
            return "B-done";
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // when: key 가 다른 두 작업을 동시에 제출한다 (A, B 서로 블로킹하면 안 됨).
            Future<String> fa = pool.submit(() -> coalescer.coalesce("key-A", workA));
            Future<String> fb = pool.submit(() -> coalescer.coalesce("key-B", workB));

            assertEquals("A-done", fa.get(5, TimeUnit.SECONDS));
            assertEquals("B-done", fb.get(5, TimeUnit.SECONDS));

            // then: 두 work 모두 상대방이 시작된 것을 확인했어야 한다(총 2회 증가) — 즉 둘 다
            // 병렬로 돌았다는 뜻이다.
            assertEquals(2, bothRanConcurrently.get(),
                    "서로 다른 키의 작업은 서로를 기다리지 않고 병렬로 진행돼야 한다");
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void 실행이_실패하면_다음_호출에서_work가_다시_실행된다() throws Exception {
        TranslationRequestCoalescer<String, String> coalescer = new TranslationRequestCoalescer<>();
        AtomicInteger executionCount = new AtomicInteger(0);
        String key = "alert-2:EN";

        // given: 첫 번째 호출의 work 는 실패한다(예: 번역 API 가 일시 오류를 낸 상황을 흉내냄).
        Callable<String> failingWork = () -> {
            executionCount.incrementAndGet();
            throw new RuntimeException("번역 API 일시 오류(흉내)");
        };
        // 두 번째 호출의 work 는 성공한다 — 같은 key 라도 실패한 첫 시도가 맵에 남아 다음 요청을
        // 영원히 막아서는 안 된다는 것을 확인하기 위함.
        Callable<String> succeedingWork = () -> {
            executionCount.incrementAndGet();
            return "재시도성공";
        };

        // when 1: 첫 호출은 예외를 그대로 전파받는다 (삼키면 안 됨 — 호출 측이 실패를 알아야 함).
        assertThrows(RuntimeException.class, () -> coalescer.coalesce(key, failingWork));

        // when 2: 같은 key 로 다시 호출하면 work 가 다시 실행돼 성공해야 한다.
        String result = coalescer.coalesce(key, succeedingWork);

        // then: 두 번 모두 실제로 work 가 실행됐어야 한다(실패 결과가 캐시돼 두 번째 호출이
        // work 를 건너뛰고 과거 예외를 재사용하면 executionCount 가 1에 머문다).
        assertEquals("재시도성공", result);
        assertEquals(2, executionCount.get(),
                "실패한 첫 시도가 다음 호출을 가로막아서는 안 된다(실패는 캐시하지 않음)");
        assertTrue(executionCount.get() > 0);
    }
}
