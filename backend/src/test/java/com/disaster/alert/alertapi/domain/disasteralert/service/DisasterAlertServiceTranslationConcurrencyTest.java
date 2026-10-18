package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.domain.disasteralert.dto.DisasterAlertDetailDto;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlertTranslationId;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertTranslationRepository;
import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import com.disaster.alert.alertapi.global.translation.OpenAiTranslationClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@code 번역캐시_동시성_PK충돌_UPSERT_원인분석.md}(TIL, 2026-08-10) 9장에서 설계된 재현
 * 시나리오를 이 저장소의 실제 클래스에 맞춰 옮긴 회귀 테스트.
 *
 * <p>{@link com.disaster.alert.alertapi.global.translation.TranslationService#ensureTranslated}
 * 는 "존재 확인 → 없으면 번역 후 저장" check-then-act 구조다. {@code save()} 는 INSERT 를 즉시
 * 실행하지 않고 예약만 하므로({@code @EmbeddedId} 복합키라 flush 가 지연된다), 두 요청이 같은
 * (alertId, language) 조합을 거의 동시에 조회하면 둘 다 "번역 없음"을 확인한 뒤 각자 저장을
 * 예약한다. {@code ensureTranslated} 호출 직후 바로 다음 줄의
 * {@code translationRepository.findByIdAlertIdAndIdLanguageCode(...)} 조회가 auto-flush 를
 * 유발해 예약해둔 INSERT 를 그제야 내보내는데, 이 시점에는 이미 {@code ensureTranslated} 내부의
 * try/catch 를 빠져나온 뒤라 PK(UNIQUE) 위반이 잡히지 않고 그대로 전파된다.
 * {@code getAlertDetail} 도 {@code @Transactional} 이라 같은 트랜잭션을 공유하므로, 이 예외는
 * 곧장 500 으로 이어지거나(즉시 전파) 트랜잭션이 rollback-only 로 마킹돼 커밋 시점
 * {@code UnexpectedRollbackException} 으로 다시 500 이 된다.
 *
 * <p>테스트 메서드에 {@code @Transactional} 을 붙이지 않는다 — 붙이면 두 스레드의 호출이 실질적으로
 * 테스트 하나의 트랜잭션/커넥션을 공유하게 되거나 각 호출 결과가 테스트 종료 시 롤백되어, 운영에서
 * 실제로 문제가 되는 "서로 다른 트랜잭션/커넥션 간 경쟁"이 가려진다
 * ({@code DisasterAlertServiceDuplicateSnRollbackTest} 와 같은 패턴).
 */
@IntegrationTest
class DisasterAlertServiceTranslationConcurrencyTest {

    @Autowired
    private DisasterAlertService disasterAlertService;

    @Autowired
    private DisasterAlertRepository disasterAlertRepository;

    @Autowired
    private DisasterAlertTranslationRepository translationRepository;

    @MockitoBean
    private OpenAiTranslationClient translationClient;

    private Long alertId;

    @AfterEach
    void tearDown() {
        if (alertId != null) {
            translationRepository.deleteById(new DisasterAlertTranslationId(alertId, "JA"));
            disasterAlertRepository.deleteById(alertId);
            alertId = null;
        }
    }

    @Test
    void 같은_알림을_같은_언어로_동시_조회해도_500이_나지_않는다() throws Exception {
        // given: 번역 캐시가 비어 있는 새 알림 (saveAndFlush 로 즉시 커밋과 동일하게 눈에 보이게 함 —
        // 테스트 메서드 자체가 @Transactional 이 아니므로 JpaRepository 메서드 각각이 자체
        // 트랜잭션으로 즉시 커밋된다)
        DisasterAlert alert = disasterAlertRepository.saveAndFlush(
                DisasterAlert.builder()
                        .sn(System.nanoTime()) // sn UNIQUE 제약 회피용 (테스트마다 유일)
                        .message("테스트 - 번역 캐시 동시성 재현용 재난문자 본문")
                        .build()
        );
        alertId = alert.getId();

        // 실제 OpenAI 호출을 목으로 대체 + 약간의 지연을 줘서, 두 스레드가 모두 "번역 없음"을
        // 확인한 뒤 저장을 예약하는 좁은 틈에 실제로 겹치도록 한다. 지연이 없으면 스케줄링에 따라
        // 재현이 불안정해질 수 있다.
        when(translationClient.translate(anyString(), eq("JA"))).thenAnswer(invocation -> {
            Thread.sleep(300);
            return "テスト翻訳";
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<DisasterAlertDetailDto> task = () -> {
            start.await();
            return disasterAlertService.getAlertDetail(alertId, "ja");
        };

        Future<DisasterAlertDetailDto> f1 = pool.submit(task);
        Future<DisasterAlertDetailDto> f2 = pool.submit(task);
        start.countDown(); // 동시 출발

        try {
            // then: 두 조회 모두 예외 없이 끝나야 한다. 현재 구현은 check-then-act 레이스로
            // PK(UNIQUE) 위반 또는 그로 인한 UnexpectedRollbackException 이 발생해 이 지점에서
            // 실패한다 (Red).
            assertDoesNotThrow(() -> {
                f1.get(10, TimeUnit.SECONDS);
                f2.get(10, TimeUnit.SECONDS);
            });
        } finally {
            pool.shutdown();
        }
    }
}
