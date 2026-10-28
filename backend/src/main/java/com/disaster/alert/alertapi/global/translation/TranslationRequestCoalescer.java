package com.disaster.alert.alertapi.global.translation;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 같은 키로 동시에 들어온 호출을 하나로 묶어(single-flight) 중복 실행을 막는 범용 유틸리티.
 *
 * <p><b>동작 방식.</b> 키마다 {@link CompletableFuture}를 하나씩 {@link ConcurrentHashMap}에
 * 두고, {@code putIfAbsent}로 "최초 진입자(실행자)"와 "뒤이어 온 합류자"를 가른다 — 실행자만
 * 실제로 {@code work}를 실행하고, 합류자는 실행자의 future가 끝나길 기다려 같은 결과를 받는다.
 * 성공·실패 어느 쪽이든 끝나면 맵에서 즉시 제거한다(실패를 남겨두면 다음 호출이 "이미 실행
 * 중"으로 오인해 과거 예외를 영원히 재사용하게 된다 — 번역 API 같은 일시 오류가 흔한 작업에는
 * 치명적).
 *
 * <p>키별로 독립적으로 동작하므로 서로 다른 키의 작업은 블로킹하지 않는다 — 전역 lock 하나로
 * 모든 키를 묶지 않는다(그렇게 하면 무관한 작업까지 직렬화돼 버린다).
 */
public class TranslationRequestCoalescer<K, V> {

    private final ConcurrentHashMap<K, CompletableFuture<V>> inFlight = new ConcurrentHashMap<>();

    /**
     * 같은 key로 동시에 들어온 호출을 병합해 work를 한 번만 실행하고 결과를 공유한다.
     *
     * @param key  병합 기준 키 (예: alertId + languageCode 조합)
     * @param work 실제로 수행할 작업 (예: 번역 API 호출)
     * @return work의 실행 결과(합류자는 실행자가 만든 결과를 그대로 받음)
     * @throws Exception work가 던진 예외를 그대로 전파(합류자도 실행자와 같은 예외를 받음)
     */
    public V coalesce(K key, Callable<V> work) throws Exception {
        CompletableFuture<V> myFuture = new CompletableFuture<>();
        // 1) 이 키로 이미 진행 중인 작업이 있는지 원자적으로 확인하면서 동시에 등록한다.
        //    putIfAbsent 가 null 을 돌려주면 "내가 최초"라는 뜻 — 내가 실행자가 된다.
        CompletableFuture<V> existing = inFlight.putIfAbsent(key, myFuture);
        if (existing != null) {
            // 2) 합류자 — 이미 등록된 실행자의 future 가 끝나길 기다려 같은 결과/예외를 공유한다.
            //    내가 만든 myFuture 는 맵에 들어가지 못했으므로 그대로 버려진다(사용 안 함).
            try {
                return existing.join();
            } catch (CompletionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception ex) {
                    throw ex;
                }
                if (cause instanceof Error err) {
                    throw err;
                }
                throw e;
            }
        }
        // 3) 실행자 — 내가 최초로 이 키를 선점했으니 실제로 work 를 실행한다.
        try {
            V result = work.call();
            myFuture.complete(result);
            return result;
        } catch (
                Exception e) {
            myFuture.completeExceptionally(e);
            throw e;
        } finally {
            // 4) 성공/실패 어느 쪽이든 맵에서 제거한다 — 다음 호출이 새로 시작할 수 있게 한다.
            //    (key, myFuture) 두 값을 모두 비교해서 지우므로, 혹시 다른 future 로 교체돼
            //    있었다면(이론상 일어나지 않지만) 그 쪽을 잘못 지우지 않는다.
            inFlight.remove(key, myFuture);
        }
    }
}
