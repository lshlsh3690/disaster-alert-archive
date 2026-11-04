package com.disaster.alert.alertapi.global.translation;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlertTranslation;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertTranslationRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TranslationService} 의 "코알레서 안쪽 재확인" 동작 단위 테스트 (순수 Mockito, Spring·DB 없음).
 *
 * <p><b>Red 상태에서 실패하는 이유</b>: 현재 {@code translateAndSaveInternal} 은 캐시를 다시 확인하지
 * 않고 곧장 {@code translationClient.translate(...)} 를 부른다. 그래서 아래 테스트는
 * {@code verify(translationClient, never()).translate(...)} 단언에서 "실제로는 1회 호출됨"으로 실패한다.
 */
class TranslationServiceTest {

    private final DisasterAlertRepository disasterAlertRepository = mock(DisasterAlertRepository.class);
    private final DisasterAlertTranslationRepository translationRepository = mock(DisasterAlertTranslationRepository.class);
    private final OpenAiTranslationClient translationClient = mock(OpenAiTranslationClient.class);

    private final TranslationService service = new TranslationService(
            disasterAlertRepository, translationRepository, translationClient, new SimpleMeterRegistry());

    /**
     * 요청 A·B 가 둘 다 선확인에서 "없음"을 본 뒤, A 가 번역·저장을 끝내고 코알레서 맵에서 빠진 다음에
     * B 가 들어오면 B 는 새 실행자가 된다. 그때 work 첫머리에서 캐시를 한 번 더 보지 않으면 OpenAI 를
     * 또 부른다(비용 낭비 + 같은 PK 저장 충돌 가능). 이 틈을 고정한다.
     */
    @Test
    void 선확인_뒤_코알레서_안에서_캐시가_생겼으면_번역_API를_부르지_않는다() {
        // given: 선확인(1번째 조회)은 "없음", 코알레서 안 재확인(2번째 조회)부터는 "있음" — A 가 그 사이 저장을 끝낸 상황
        DisasterAlertTranslation existing = DisasterAlertTranslation.of(1L, "JA", "既存の翻訳", null, null);
        when(translationRepository.findByIdAlertIdAndIdLanguageCode(1L, "JA"))
                .thenReturn(Optional.empty(), Optional.of(existing));
        // 알림 본문은 있어서, 재확인이 없다면 번역 호출까지 그대로 진행되는 조건
        when(disasterAlertRepository.findById(1L))
                .thenReturn(Optional.of(DisasterAlert.builder().sn(1L).message("테스트 본문").build()));

        // when: B 요청 — 선확인 통과 → 코알레서 새 실행자로 진입
        service.ensureTranslated(1L, SupportedLanguage.JA);

        // then: 재확인에서 캐시를 발견했으므로 OpenAI 호출도, 중복 저장도 없어야 한다
        verify(translationClient, never()).translate(anyString(), anyString());
        verify(translationRepository, never()).save(any());
    }
}
