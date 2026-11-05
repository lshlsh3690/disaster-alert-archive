package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import com.disaster.alert.alertapi.domain.event.model.DisasterEventTranslation;
import com.disaster.alert.alertapi.domain.event.repository.DisasterEventRepository;
import com.disaster.alert.alertapi.domain.event.repository.DisasterEventTranslationRepository;
import com.disaster.alert.alertapi.global.translation.OpenAiTranslationClient;
import com.disaster.alert.alertapi.global.translation.SupportedLanguage;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EventTranslationService} 의 중복 번역 방지 단위 테스트 (순수 Mockito, Spring·DB 없음).
 *
 * <p><b>Red 상태에서 실패하는 이유</b>
 * <ul>
 *   <li>동시 요청 테스트 — 코알레서가 없어 두 스레드가 각자 {@code translate} 를 불러 호출 횟수가
 *       1이 아니라 2가 된다({@code times(1)} 단언 실패).</li>
 *   <li>직전 재확인 테스트 — {@code translateAndSaveInternal} 이 캐시를 다시 보지 않아
 *       {@code translate} 가 1회 불린다({@code never()} 단언 실패).</li>
 * </ul>
 */
class EventTranslationServiceTest {

    private final DisasterEventRepository disasterEventRepository = mock(DisasterEventRepository.class);
    private final DisasterEventTranslationRepository translationRepository = mock(DisasterEventTranslationRepository.class);
    private final OpenAiTranslationClient translationClient = mock(OpenAiTranslationClient.class);

    private final EventTranslationService service =
            new EventTranslationService(disasterEventRepository, translationRepository, translationClient);

    /**
     * 같은 이벤트·같은 언어로 동시에 들어온 요청이 각자 OpenAI 를 부르면 비용·지연이 낭비된다.
     * 재난문자 경로와 같은 single-flight 로 하나만 실행하고 나머지는 결과를 공유해야 한다.
     */
    @Test
    void 같은_이벤트를_같은_언어로_동시_요청하면_번역_API는_한_번만_호출된다() throws Exception {
        // given: 캐시는 계속 비어 있고, 번역 호출이 300ms 걸리게 해 두 스레드가 확실히 겹치도록 한다
        // (지연이 없으면 첫 스레드가 먼저 끝나 겹침이 재현되지 않을 수 있음)
        when(disasterEventRepository.findById(7L))
                .thenReturn(Optional.of(DisasterEvent.builder().eventTitle("서울 호우 (2026-11-04)").build()));
        when(translationRepository.findByIdEventIdAndIdLanguageCode(7L, "JA")).thenReturn(Optional.empty());
        when(translationClient.translate(anyString(), eq("JA"))).thenAnswer(invocation -> {
            Thread.sleep(300);
            return "テスト";
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            // when: 두 스레드가 start 신호와 동시에 같은 키(7L, JA)로 번역을 요청
            Runnable task = () -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                service.ensureTranslated(7L, SupportedLanguage.JA);
            };
            Future<?> f1 = pool.submit(task);
            Future<?> f2 = pool.submit(task);
            start.countDown();
            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);

            // then: 한 스레드만 실제 번역을 실행했어야 한다 (현재는 코알레서가 없어 2회)
            verify(translationClient, times(1)).translate(anyString(), eq("JA"));
        } finally {
            pool.shutdown();
        }
    }

    /**
     * 선확인 뒤 번역 직전에 다른 요청이 이미 저장을 끝낸 경우, work 첫머리의 재확인이 없으면
     * 이미 있는 번역을 위해 OpenAI 를 또 부른다.
     */
    @Test
    void 선확인_뒤_번역_직전에_캐시가_생겼으면_번역_API를_부르지_않는다() {
        // given: 선확인(1번째 조회)은 "없음", 번역 직전 재확인(2번째 조회)부터는 "있음"
        DisasterEventTranslation existing = mock(DisasterEventTranslation.class);
        when(translationRepository.findByIdEventIdAndIdLanguageCode(7L, "JA"))
                .thenReturn(Optional.empty(), Optional.of(existing));
        // 제목은 있어서, 재확인이 없다면 번역 호출까지 그대로 진행되는 조건
        when(disasterEventRepository.findById(7L))
                .thenReturn(Optional.of(DisasterEvent.builder().eventTitle("서울 호우 (2026-11-04)").build()));

        // when
        service.ensureTranslated(7L, SupportedLanguage.JA);

        // then: 재확인에서 캐시를 발견했으므로 번역도, 저장(save)도 없어야 한다
        verify(translationClient, never()).translate(anyString(), anyString());
        verify(translationRepository, never()).save(any());
    }
}
