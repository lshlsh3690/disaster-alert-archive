package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import com.disaster.alert.alertapi.domain.event.model.DisasterEventTranslation;
import com.disaster.alert.alertapi.domain.event.repository.DisasterEventRepository;
import com.disaster.alert.alertapi.domain.event.repository.DisasterEventTranslationRepository;
import com.disaster.alert.alertapi.global.translation.DeepLTranslationClient;
import com.disaster.alert.alertapi.global.translation.SupportedLanguage;
import com.disaster.alert.alertapi.global.translation.TranslationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 이벤트 제목 다국어 번역 — alert {@code TranslationService} 패턴 복제(제목만).
 *
 * <p>조회 시 lazy: 해당 언어 캐시 없으면 {@code event_title} 을 DeepL 로 번역해 저장.
 * 지역/유형은 별도 필드로 안 줌(제목 안에 포함). 타임라인 알림 번역은 alert 쪽 재사용.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventTranslationService {

    private final DisasterEventRepository disasterEventRepository;
    private final DisasterEventTranslationRepository translationRepository;
    private final DeepLTranslationClient deepLClient;
    private final TranslationProperties properties;

    /** 상세 조회 lazy 번역 — 단건. */
    @Transactional
    public void ensureTranslated(Long eventId, SupportedLanguage language) {
        if (!properties.isEnabled()) {
            return;
        }
        if (translationRepository.findByIdEventIdAndIdLanguageCode(eventId, language.getDbCode()).isPresent()) {
            return;
        }
        translateAndSaveInternal(eventId, language);
    }

    /** 목록 조회 lazy 번역 — 페이지 내 미번역분만 일괄 DeepL. */
    @Transactional
    public void ensureTranslatedBatch(List<Long> eventIds, SupportedLanguage language) {
        if (!properties.isEnabled() || eventIds == null || eventIds.isEmpty()) {
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
                translateAndSaveInternal(eventId, language);
            } catch (Exception e) {
                log.warn("이벤트 제목 번역 단건 실패(스킵): eventId={}, lang={}, reason={}",
                        eventId, language.getDbCode(), e.getMessage());
            }
        }
    }

    private void translateAndSaveInternal(Long eventId, SupportedLanguage language) {
        DisasterEvent event = disasterEventRepository.findById(eventId).orElse(null);
        if (event == null || event.getEventTitle() == null || event.getEventTitle().isBlank()) {
            return;
        }
        String targetLang = language.getDbCode();
        String translatedTitle = deepLClient.translate(event.getEventTitle(), targetLang);
        translationRepository.save(DisasterEventTranslation.of(eventId, targetLang, translatedTitle));
        log.info("이벤트 제목 번역 완료: eventId={}, lang={}", eventId, targetLang);
    }
}
