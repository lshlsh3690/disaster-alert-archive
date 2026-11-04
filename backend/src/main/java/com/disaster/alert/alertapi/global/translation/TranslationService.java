package com.disaster.alert.alertapi.global.translation;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlertTranslation;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlertTranslationId;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertTranslationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 재난문자 번역 서비스 (OpenAI 번역 호출 + DB 캐시).
 *
 * <p><b>번역 대상 필드</b>
 * <ul>
 *   <li>message — 재난문자 본문 (필수)</li>
 *   <li>disasterType — 재난 유형 (예: "호우주의보")</li>
 * </ul>
 *
 * <p><b>지역명(region_names)은 여기서 번역하지 않는다.</b>
 * {@code legal_district_translation} 테이블에서 조회한다.
 * (같은 법정동이 여러 재난문자에 반복 등장하므로 DB 캐시가 훨씬 효율적)
 *
 * <p><b>동작 모드</b>
 * <ul>
 *   <li>{@link #translateAndSaveAsync(Long)} — 스케줄러용 (기존). 지원하는 모든 언어로 비동기 번역.</li>
 *   <li>{@link #ensureTranslated(Long, SupportedLanguage)} — 단건 lazy 번역. 상세 조회에서 사용.</li>
 *   <li>{@link #ensureTranslatedBatch(List, SupportedLanguage)} — 목록 lazy 번역. 검색/최신 목록에서 사용.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationService {

    /** 이미 캐시가 있어 번역 API를 아예 타지 않은 경우 (ensureTranslated/Batch 공용). */
    private static final String METRIC_CACHE_HIT = "translation.cache.hit";
    /** 같은 키 동시 요청에서 "내가 실제로 work(번역+저장)를 실행한 쪽"이었던 경우. */
    private static final String METRIC_COALESCE_EXECUTED = "translation.coalesce.executed";
    /** 같은 키 동시 요청에서 "다른 쪽이 실행 중이라 결과를 공유만 받은 쪽"이었던 경우. */
    private static final String METRIC_COALESCE_JOINED = "translation.coalesce.joined";

    private final DisasterAlertRepository disasterAlertRepository;
    private final DisasterAlertTranslationRepository translationRepository;
    private final OpenAiTranslationClient translationClient;
    private final MeterRegistry meterRegistry;

    /**
     * 같은 (alertId, language) 키로 동시에 들어온 요청을 병합(single-flight)한다 — Spring 빈이
     * 아니라 이 서비스 인스턴스가 직접 들고 있는 순수 유틸리티 필드다(필드 초기화식으로 바로
     * 생성해 Lombok {@code @RequiredArgsConstructor}의 생성자 주입 대상에서 제외됨).
     *
     * <p>왜 필요한가: 캐시 미스가 동시에 여러 요청에서 발생하면(예: 같은 알림을 같은 언어로
     * 동시 조회) 각 요청이 각자 OpenAI 번역 API를 호출하는 낭비(비용·지연)가 생기고, 그 중복
     * 호출들이 같은 (alertId, languageCode) PK로 저장을 시도하면 충돌도 날 수 있다. 이 코알레서가
     * 같은 키의 동시 호출을 하나로 묶어 실제 번역+저장은 단 한 번만 일어나게 한다.
     */
    private final TranslationRequestCoalescer<DisasterAlertTranslationId, DisasterAlertTranslation> coalescer =
            new TranslationRequestCoalescer<>();

    /**
     * 스케줄러용 — 새 재난문자 저장 시 {@link SupportedLanguage}에 등록된 모든 언어로
     * 즉시 비동기 번역 (EN/JA/ZH).
     *
     * <p>언어 하나가 번역 API 오류 등으로 실패해도 나머지 언어는 계속 진행한다.
     *
     * <p><b>메서드 레벨 {@code @Transactional}을 붙이지 않는다</b> — 공통 이유는
     * {@link com.disaster.alert.alertapi.domain.event.service.EventTranslationService} 클래스
     * javadoc에 있다. 여기에는 이유가 하나 더 있다: 붙어 있으면 EN/JA/ZH 세 언어가 한 트랜잭션으로
     * 묶여, 한 언어의 저장 실패가 rollback-only를 찍어 이미 번역해 둔 다른 언어까지 함께 버린다.
     * 위에 적은 "언어 하나가 실패해도 나머지는 계속"이 저장 단계에서 깨지는 셈이었다.
     */
    @Async("translationExecutor")
    public void translateAndSaveAsync(Long alertId) {
        for (SupportedLanguage language : SupportedLanguage.values()) {
            try {
                coalesceTranslate(alertId, language);
            } catch (Exception e) {
                log.warn("수집 시점 번역 실패(스킵): alertId={}, lang={}, reason={}",
                        alertId, language.getDbCode(), e.getMessage());
            }
        }
    }

    /**
     * 상세 조회 lazy 번역 — 특정 재난문자가 해당 언어로 번역되어 있지 않으면
     * 번역 API 호출 → 저장 후 반환. 이미 있으면 그대로 반환.
     *
     * <p>번역 실패는 삼킨다. 예외를 그대로 올리면 상세 조회 전체가 500 이 되는데, 번역은 표시
     * 부가 기능일 뿐이라 실패해도 원문으로 폴백해 보여주는 게 맞다. 실제로 DeepL 쿼터가
     * 소진됐을 때 외국어 상세 조회가 전부 500 으로 떨어지고 에러 로그가 도배됐다
     * ({@link #ensureTranslatedBatch}는 원래부터 단건 실패를 삼켜 목록만 멀쩡했다).
     *
     * <p>이 메서드에는 더 이상 {@code @Transactional}을 붙이지 않는다 — 과거에는 이 메서드가
     * 통째로 트랜잭션이라, "캐시 확인 → OpenAI 호출(네트워크, 수백 ms~수 초) → 저장"이 전부
     * 하나의 DB 트랜잭션(= 커넥션 점유) 안에서 일어났다. 이제 각 리포지토리 호출은 Spring Data가
     * 호출마다 자체 트랜잭션을 열어주므로 원자성 문제가 없고, 느린 OpenAI 호출은 어떤 트랜잭션도
     * 열지 않은 채로 실행된다.
     */
    public void ensureTranslated(Long alertId, SupportedLanguage language) {
        if (translationRepository.findByIdAlertIdAndIdLanguageCode(alertId, language.getDbCode()).isPresent()) {
            meterRegistry.counter(METRIC_CACHE_HIT).increment();
            return;
        }
        try {
            coalesceTranslate(alertId, language);
        } catch (Exception e) {
            log.warn("상세 조회 lazy 번역 실패(원문 폴백): alertId={}, lang={}, reason={}",
                    alertId, language.getDbCode(), e.getMessage());
        }
    }

    /**
     * 목록 조회 lazy 번역 — 페이지에 담긴 재난문자들 중 해당 언어로 번역되지 않은 것을 일괄 처리.
     *
     * <p>호출 흐름:
     * <ol>
     *   <li>이미 번역된 alertId 를 DB 에서 조회 (1쿼리)</li>
     *   <li>누락된 alertId 만 OpenAI 로 번역 → 저장</li>
     * </ol>
     *
     * <p>주의: 누락 건수가 많을수록 응답이 느려진다 (번역 API 호출 N회).
     * 사용자가 한국어 → 일본어 전환 첫 요청 시 가장 느림. 이후 캐시 적중.
     *
     * <p>{@code ensureTranslated}와 같은 이유로 {@code @Transactional}을 떼어냈다 — 누락 건수가
     * 많을 때 OpenAI 호출 N번을 하나의 트랜잭션(커넥션)으로 묶어 두는 것이 더 나쁘다.
     *
     * @param alertIds 페이지에 포함된 재난문자 ID 리스트
     * @param language 대상 언어
     */
    public void ensureTranslatedBatch(List<Long> alertIds, SupportedLanguage language) {
        if (alertIds == null || alertIds.isEmpty()) {
            return;
        }

        // 이미 번역된 alertId 조회
        Set<Long> existing = translationRepository
                .findByIdAlertIdInAndIdLanguageCode(alertIds, language.getDbCode())
                .stream()
                .map(t -> t.getId().getAlertId())
                .collect(Collectors.toSet());
        if (!existing.isEmpty()) {
            meterRegistry.counter(METRIC_CACHE_HIT).increment(existing.size());
        }

        // 누락된 alertId 만 번역
        List<Long> missing = alertIds.stream()
                .filter(id -> !existing.contains(id))
                .toList();

        if (missing.isEmpty()) {
            return;
        }

        log.info("일괄 lazy 번역 시작: lang={}, missing={}건", language.getDbCode(), missing.size());
        for (Long alertId : missing) {
            try {
                coalesceTranslate(alertId, language);
            } catch (Exception e) {
                // 한 건이 실패해도 나머지는 계속 진행 (번역 API 일시 오류 등)
                log.warn("일괄 번역 중 단건 실패 (스킵): alertId={}, lang={}, reason={}",
                        alertId, language.getDbCode(), e.getMessage());
            }
        }
    }

    /**
     * {@link #translateAndSaveInternal}을 {@link #coalescer}로 감싸는 공용 진입점 — lazy 단건,
     * lazy 배치, 스케줄러 세 호출 지점이 모두 이 메서드를 거친다.
     *
     * <p>병합 키는 새 문자열을 만들지 않고 이미 있는 {@link DisasterAlertTranslationId}(JPA
     * {@code @EmbeddedId}라 equals/hashCode가 이미 올바름)를 그대로 쓴다 — alertId+languageCode
     * 복합키가 필요하다는 이유로 별도 키 타입을 또 만들 필요가 없었다.
     *
     * <p>{@code executed} 플래그는 work 람다 본문 안에서만 true로 세팅된다 — 코알레서에 합류한
     * (실행하지 않고 결과만 공유받은) 호출은 이 람다 자체를 타지 않으므로 false로 남는다. 성공·
     * 실패 어느 쪽이든 반드시 한 번 집계되도록 try/finally로 묶는다(실패해도 "내가 실행자였는지
     * 합류자였는지"는 알고 있어야 메트릭이 의미가 있다).
     *
     * @throws Exception translateAndSaveInternal(또는 내가 합류한 실행자)이 던진 예외를 그대로 전파
     */
    private void coalesceTranslate(Long alertId, SupportedLanguage language) throws Exception {
        DisasterAlertTranslationId key = new DisasterAlertTranslationId(alertId, language.getDbCode());
        AtomicBoolean executed = new AtomicBoolean(false);
        try {
            coalescer.coalesce(key, () -> {
                executed.set(true);
                return translateAndSaveInternal(alertId, language);
            });
        } finally {
            meterRegistry.counter(executed.get() ? METRIC_COALESCE_EXECUTED : METRIC_COALESCE_JOINED).increment();
        }
    }

    /**
     * 실제 번역 + 저장 로직 (내부 공용) — 항상 {@link #coalesceTranslate}를 통해서만 호출된다.
     * 같은 (alertId, language) 키로 동시에 이 메서드에 두 번 들어오는 일 자체가 코알레서가
     * 막아주므로, 더 이상 "중복 호출 시 PK 충돌"을 이 메서드 레벨에서 걱정하지 않는다.
     *
     * <p>OpenAI 호출(네트워크, 수백 ms~수 초)은 트랜잭션 밖에서 실행하고, 저장만 {@link
     * #saveTranslation}의 짧은 트랜잭션 범위로 분리했다 — 과거에는 이 메서드 전체가 호출 측의
     * {@code @Transactional} 안에서 실행돼 느린 네트워크 호출 동안 DB 커넥션을 쥐고 있었다.
     *
     * <p><b>첫머리에서 캐시를 한 번 더 확인하는 이유.</b> 호출 측({@code ensureTranslated} 등)의
     * 선확인은 코알레서에 들어가기 <i>전</i>의 확인이라, 그 뒤에 다른 요청 A 가 번역·저장을 끝내고
     * 코알레서 맵에서 빠져나가면 늦게 온 요청 B 는 합류할 대상이 없어 새 실행자가 되고, 이미
     * 있는 번역을 위해 OpenAI 를 또 부르게 된다. 실행자가 된 직후 여기서 다시 확인해야 이 틈이
     * 닫힌다. (선확인은 코알레서 진입 자체를 아끼는 빠른 경로로 그대로 둔다.)
     *
     * @return 저장된(또는 이미 저장돼 있던) 번역 결과. 번역 대상이 없으면(본문 없음) null.
     */
    private DisasterAlertTranslation translateAndSaveInternal(Long alertId, SupportedLanguage language) {
        // 1) 실행자가 된 직후 캐시를 다시 본다 — 선확인과 코알레서 진입 사이에 다른 요청이 저장을 끝냈을 수 있다.
        //    있으면 번역 API·저장 없이 그 값을 돌려준다. cache.hit 카운터는 올리지 않는다 — 이 호출은
        //    이미 coalesce.executed 로 집계돼 이중 집계가 되고 두 지표의 의미가 흐려진다.
        Optional<DisasterAlertTranslation> cached =
                translationRepository.findByIdAlertIdAndIdLanguageCode(alertId, language.getDbCode());
        if (cached.isPresent()) {
            return cached.get();
        }
        try {
            DisasterAlert alert = disasterAlertRepository.findById(alertId).orElse(null);
            if (alert == null || alert.getMessage() == null || alert.getMessage().isBlank()) {
                return null;
            }

            String targetLang = language.getDbCode();

            String translatedMessage = translationClient.translate(alert.getMessage(), targetLang);

            String translatedType = null;
            if (alert.getDisasterType() != null && !alert.getDisasterType().isBlank()) {
                try {
                    translatedType = translationClient.translate(alert.getDisasterType(), targetLang);
                } catch (Exception e) {
                    log.warn("유형 번역 실패: alertId={}, lang={}, reason={}", alertId, targetLang, e.getMessage());
                }
            }

            // 지역명(region_names)은 legal_district_translation 에서 처리하므로 여기서는 null 로 저장.
            DisasterAlertTranslation saved = saveTranslation(alertId, targetLang, translatedMessage, translatedType);
            log.info("번역 완료: alertId={}, lang={}", alertId, targetLang);
            return saved;

        } catch (Exception e) {
            log.warn("번역 실패: alertId={}, lang={}, reason={}", alertId, language.getDbCode(), e.getMessage());
            throw e; // 호출 측(batch)에서 단건 실패를 잡을 수 있도록 재던짐
        }
    }

    /**
     * 번역 결과 저장만 담당하는 좁은 트랜잭션 범위 — OpenAI 호출이 끝난 뒤의 짧은 DB 쓰기만
     * 트랜잭션으로 감싸, 느린 네트워크 호출 동안 커넥션을 쥐고 있지 않게 분리했다.
     *
     * <p><b>알아둘 점</b>: 이 메서드는 같은 클래스 안에서 {@link #translateAndSaveInternal}이
     * 직접 호출한다(self-invocation). Spring AOP는 프록시를 거치지 않는 self-invocation 호출을
     * 가로채지 못하므로, 이 {@code @Transactional}은 그 경로에서는 실질적으로 새 트랜잭션을 열지
     * 않는다. 그래도 안전한 이유는 {@code translationRepository.save(...)} 자체가 Spring Data
     * 리포지토리 프록시를 통해 호출될 때마다 독립적인 트랜잭션을 스스로 열기 때문이다 — 이는
     * {@code DisasterAlertService}의 {@code findById} 등 다른 리포지토리 호출이 트랜잭션
     * 어노테이션 없이도 원자적인 것과 같은 이유다. 이 어노테이션은 "저장은 여기서 끝난다"는
     * 의도를 문서화하는 역할로 남겨둔다.
     */
    @Transactional
    protected DisasterAlertTranslation saveTranslation(Long alertId, String targetLang,
                                                         String translatedMessage, String translatedType) {
        return translationRepository.save(
                DisasterAlertTranslation.of(alertId, targetLang, translatedMessage, translatedType, null)
        );
    }
}
