package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import com.disaster.alert.alertapi.domain.event.model.DisasterEventTranslation;
import com.disaster.alert.alertapi.domain.event.model.DisasterEventTranslationId;
import com.disaster.alert.alertapi.domain.event.repository.DisasterEventRepository;
import com.disaster.alert.alertapi.domain.event.repository.DisasterEventTranslationRepository;
import com.disaster.alert.alertapi.global.translation.OpenAiTranslationClient;
import com.disaster.alert.alertapi.global.translation.SupportedLanguage;
import com.disaster.alert.alertapi.global.translation.TranslationRequestCoalescer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 이벤트 제목 다국어 번역 — alert {@code TranslationService} 패턴 복제(제목만).
 *
 * <p>조회 시 lazy: 해당 언어 캐시 없으면 {@code event_title} 을 OpenAI 로 번역해 저장.
 * 지역/유형은 별도 필드로 안 줌(제목 안에 포함). 타임라인 알림 번역은 alert 쪽 재사용.
 *
 * <p><b>번역 경로에 {@code @Transactional} 을 붙이지 않는 이유</b> — 이 클래스뿐 아니라 호출부
 * {@code EventQueryService.list/detail}, 재난문자 쪽 {@code TranslationService} 와 그 호출부까지
 * 모두 같은 규칙을 따른다. 이 설명이 그 규칙의 기준점이다.
 *
 * <p>번역 캐시 테이블은 복합 {@code @EmbeddedId} 라 {@code save()} 가 INSERT 를 즉시 실행하지 않고
 * 예약만 한다. 그래서 저장이 긴 트랜잭션에 합류하면 INSERT 가 커밋 시점까지 미뤄지고 두 가지가
 * 한꺼번에 깨진다. (1) PK 충돌이 번역 서비스의 try/catch 를 이미 벗어난 뒤에 터져 원문 폴백 대신
 * HTTP 500 이 된다(2026-08 에 실제로 겪은 결함). (2) 커밋 전 행은 다른 트랜잭션에 보이지 않으므로
 * (READ COMMITTED) 아래 요청 병합의 캐시 재확인이 무력해져 OpenAI 를 중복 호출한다.
 *
 * <p>2026-11-04 에 이벤트 제목 저장을 {@code upsert} 에서 {@code save()} 로 되돌리면서 이 결함이
 * 실제로 되살아났고, 호출부 {@code @Transactional} 을 걷어 닫았다. <b>요청 병합과 트랜잭션 경계는
 * 세트다</b> — 하나만 넣으면 UPSERT 라는 안전망까지 걷어낸 만큼 전보다 나빠진다.
 *
 * <p>{@code @Transactional(readOnly = true)} 로 바꾸는 것도 답이 아니다. 합류는 그대로인 데다,
 * {@code readOnly} 세션은 flush 를 하지 않아 합류한 INSERT 가 예외 없이 조용히 사라질 수 있다 —
 * 500 보다 찾기 어려운 증상이 된다.
 *
 * <p>트랜잭션 밖에서 안전한 근거는 "연관관계가 없다"가 아니라 <b>"연관관계 필드를 건드리지
 * 않는다"</b> 이다. 이 경로가 읽는 {@code DisasterEvent} 와 이벤트 번역 엔티티에는 연관관계가 없지만,
 * 타임라인에서 쓰는 {@code DisasterAlertTranslation} 에는 {@code disasterAlert} LAZY
 * {@code @ManyToOne} 이 있다. 지금은 컬럼 getter 만 읽어 안전하니, 그 연관 필드에는 접근하지 말 것.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventTranslationService {

    private final DisasterEventRepository disasterEventRepository;
    private final DisasterEventTranslationRepository translationRepository;
    private final OpenAiTranslationClient translationClient;

    /**
     * 같은 (eventId, language) 키로 동시에 들어온 요청을 병합(single-flight)한다. 재난문자 쪽
     * {@code TranslationService} 와 같은 구조다 — 필드 초기화식이라 생성자 주입 대상이 아니다.
     *
     * <p>왜 필요한가: 캐시 미스가 동시에 여러 요청에서 나면 각자 OpenAI 를 불러 비용·지연이 낭비되고,
     * 같은 복합 PK 로 저장을 시도해 충돌할 수 있다. 같은 키의 동시 호출을 하나로 묶어 번역+저장은
     * 한 번만 일어나게 한다. 서버가 여러 대면 인스턴스마다 따로 돌아 인스턴스 사이는 병합되지 않는다.
     */
    private final TranslationRequestCoalescer<DisasterEventTranslationId, DisasterEventTranslation> coalescer =
            new TranslationRequestCoalescer<>();

    /**
     * 상세 조회 lazy 번역 — 단건.
     *
     * <p>alert 쪽 {@code TranslationService.ensureTranslated} 와 같은 이유로 실패를 삼킨다 —
     * 제목 번역이 안 됐다고 이벤트 상세 조회 전체를 500 으로 떨어뜨릴 이유가 없다.
     */
    public void ensureTranslated(Long eventId, SupportedLanguage language) {
        // 1) 선확인 — 캐시가 있으면 코알레서에 들어가지 않고 끝낸다(가장 흔한 경로).
        if (translationRepository.findByIdEventIdAndIdLanguageCode(eventId, language.getDbCode()).isPresent()) {
            return;
        }
        try {
            coalesceTranslate(eventId, language);
        } catch (Exception e) {
            log.warn("이벤트 제목 lazy 번역 실패(원문 폴백): eventId={}, lang={}, reason={}",
                    eventId, language.getDbCode(), e.getMessage());
        }
    }

    /** 목록 조회 lazy 번역 — 페이지 내 미번역분만 일괄 번역. */
    public void ensureTranslatedBatch(List<Long> eventIds, SupportedLanguage language) {
        if (eventIds == null || eventIds.isEmpty()) {
            return;
        }
        Set<Long> existing = translationRepository
                .findByIdEventIdInAndIdLanguageCode(eventIds, language.getDbCode())
                .stream()
                .map(t -> t.getId().getEventId())
                .collect(Collectors.toSet());

        List<Long> missing = eventIds.stream().filter(id -> !existing.contains(id)).toList();
        if (missing.isEmpty()) {
            return;
        }
        log.info("이벤트 제목 일괄 번역: lang={}, missing={}건", language.getDbCode(), missing.size());
        for (Long eventId : missing) {
            try {
                coalesceTranslate(eventId, language);
            } catch (Exception e) {
                // 한 건이 실패해도 나머지는 계속 진행 (번역 API 일시 오류 등)
                log.warn("이벤트 제목 번역 단건 실패(스킵): eventId={}, lang={}, reason={}",
                        eventId, language.getDbCode(), e.getMessage());
            }
        }
    }

    /**
     * {@link #translateAndSaveInternal} 을 {@link #coalescer} 로 감싸는 공용 진입점 — 단건·배치가
     * 모두 이 메서드를 거친다. 병합 키는 기존 {@link DisasterEventTranslationId}(equals/hashCode 보유)를
     * 그대로 쓴다.
     *
     * @throws Exception 실행자(또는 내가 합류한 실행자)가 던진 예외를 그대로 전파
     */
    private void coalesceTranslate(Long eventId, SupportedLanguage language) throws Exception {
        coalescer.coalesce(new DisasterEventTranslationId(eventId, language.getDbCode()),
                () -> translateAndSaveInternal(eventId, language));
    }

    /**
     * 실제 번역 + 저장 — 항상 {@link #coalesceTranslate} 를 통해서만 호출된다.
     *
     * @return 저장된(또는 이미 저장돼 있던) 번역. 번역할 제목이 없으면 null.
     */
    private DisasterEventTranslation translateAndSaveInternal(Long eventId, SupportedLanguage language) {
        String targetLang = language.getDbCode();

        // 1) 실행자가 된 직후 캐시를 다시 본다 — 선확인과 코알레서 진입 사이에 다른 요청 A 가 번역·저장을
        //    끝내고 맵에서 빠졌다면, 늦게 온 B 는 합류할 대상이 없어 새 실행자가 되어 API 를 또 부르게 된다.
        Optional<DisasterEventTranslation> cached =
                translationRepository.findByIdEventIdAndIdLanguageCode(eventId, targetLang);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2) 번역할 제목이 없으면 할 일이 없다(이벤트 삭제·빈 제목) — 실패가 아니라 null 반환.
        DisasterEvent event = disasterEventRepository.findById(eventId).orElse(null);
        if (event == null || event.getEventTitle() == null || event.getEventTitle().isBlank()) {
            return null;
        }

        // 3) 번역 후 저장. 같은 키 동시 호출은 코알레서가 막으므로 DB upsert 에 기대지 않고 save() 를 쓴다.
        String translatedTitle = translationClient.translate(event.getEventTitle(), targetLang);
        DisasterEventTranslation saved =
                translationRepository.save(DisasterEventTranslation.of(eventId, targetLang, translatedTitle));
        log.info("이벤트 제목 번역 완료: eventId={}, lang={}", eventId, targetLang);
        return saved;
    }
}
