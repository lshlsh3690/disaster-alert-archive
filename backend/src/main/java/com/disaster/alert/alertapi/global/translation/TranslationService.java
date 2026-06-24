package com.disaster.alert.alertapi.global.translation;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlertTranslation;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertTranslationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 재난문자 번역 서비스 (DeepL API 호출 + DB 캐시).
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
 *   <li>{@link #translateAndSaveAsync(Long)} — 스케줄러용. 지원 언어(EN/JA/ZH) 전체를 비동기 번역.</li>
 *   <li>{@link #ensureTranslated(Long, SupportedLanguage)} — 단건 lazy 번역. 상세 조회에서 사용.</li>
 *   <li>{@link #ensureTranslatedBatch(List, SupportedLanguage)} — 목록 lazy 번역. 검색/최신 목록에서 사용.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationService {

    private final DisasterAlertRepository disasterAlertRepository;
    private final DisasterAlertTranslationRepository translationRepository;
    private final DeepLTranslationClient deepLClient;
    private final TranslationProperties properties;

    /**
     * 스케줄러용 — 새 재난문자 저장 시 지원하는 모든 언어로 자동 번역 (비동기).
     *
     * <p>지원 언어(영어/일본어/중국어) 전체를 미리 번역해 두어, 사용자가 어떤 언어로
     * 조회하더라도 lazy 번역 대기 없이 즉시 캐시된 결과를 받도록 한다.
     *
     * <p>한 언어 번역이 실패해도(DeepL 일시 오류 등) 나머지 언어는 계속 진행한다.
     */
    @Async("translationExecutor")
    @Transactional
    public void translateAndSaveAsync(Long alertId) {
        if (!properties.isEnabled()) {
            return;
        }
        for (SupportedLanguage language : SupportedLanguage.values()) {
            // 이미 번역되어 있으면 스킵 (PK 충돌 방지 + DeepL 토큰 절감)
            if (translationRepository.findByIdAlertIdAndIdLanguageCode(alertId, language.getDbCode()).isPresent()) {
                continue;
            }
            try {
                translateAndSaveInternal(alertId, language);
            } catch (Exception e) {
                // 한 언어가 실패해도 나머지 언어는 계속 번역 진행
                log.warn("스케줄러 번역 중 단건 실패 (스킵): alertId={}, lang={}, reason={}",
                        alertId, language.getDbCode(), e.getMessage());
            }
        }
    }

    /**
     * 상세 조회 lazy 번역 — 특정 재난문자가 해당 언어로 번역되어 있지 않으면
     * DeepL 호출 → 저장 후 반환. 이미 있으면 그대로 반환.
     */
    @Transactional
    public void ensureTranslated(Long alertId, SupportedLanguage language) {
        if (!properties.isEnabled()) {
            return;
        }
        if (translationRepository.findByIdAlertIdAndIdLanguageCode(alertId, language.getDbCode()).isPresent()) {
            return;
        }
        translateAndSaveInternal(alertId, language);
    }

    /**
     * 목록 조회 lazy 번역 — 페이지에 담긴 재난문자들 중 해당 언어로 번역되지 않은 것을 일괄 처리.
     *
     * <p>호출 흐름:
     * <ol>
     *   <li>이미 번역된 alertId 를 DB 에서 조회 (1쿼리)</li>
     *   <li>누락된 alertId 만 DeepL 로 번역 → 저장</li>
     * </ol>
     *
     * <p>주의: 누락 건수가 많을수록 응답이 느려진다 (DeepL 호출 N회).
     * 사용자가 한국어 → 일본어 전환 첫 요청 시 가장 느림. 이후 캐시 적중.
     *
     * @param alertIds 페이지에 포함된 재난문자 ID 리스트
     * @param language 대상 언어
     */
    @Transactional
    public void ensureTranslatedBatch(List<Long> alertIds, SupportedLanguage language) {
        if (!properties.isEnabled() || alertIds == null || alertIds.isEmpty()) {
            return;
        }

        // 이미 번역된 alertId 조회
        Set<Long> existing = translationRepository
                .findByIdAlertIdInAndIdLanguageCode(alertIds, language.getDbCode())
                .stream()
                .map(t -> t.getId().getAlertId())
                .collect(Collectors.toSet());

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
                translateAndSaveInternal(alertId, language);
            } catch (Exception e) {
                // 한 건이 실패해도 나머지는 계속 진행 (DeepL 일시 오류 등)
                log.warn("일괄 번역 중 단건 실패 (스킵): alertId={}, lang={}, reason={}",
                        alertId, language.getDbCode(), e.getMessage());
            }
        }
    }

    /**
     * 실제 번역 + 저장 로직 (내부 공용).
     *
     * <p>중복 호출 시 PK 충돌이 발생할 수 있으므로 호출 측에서 존재 여부를 사전에 체크해야 한다.
     */
    private void translateAndSaveInternal(Long alertId, SupportedLanguage language) {
        try {
            DisasterAlert alert = disasterAlertRepository.findById(alertId).orElse(null);
            if (alert == null || alert.getMessage() == null || alert.getMessage().isBlank()) {
                return;
            }

            String targetLang = language.getDbCode();

            String translatedMessage = deepLClient.translate(alert.getMessage(), targetLang);

            String translatedType = null;
            if (alert.getDisasterType() != null && !alert.getDisasterType().isBlank()) {
                try {
                    translatedType = deepLClient.translate(alert.getDisasterType(), targetLang);
                } catch (Exception e) {
                    log.warn("유형 번역 실패: alertId={}, lang={}, reason={}", alertId, targetLang, e.getMessage());
                }
            }

            // 지역명(region_names)은 legal_district_translation 에서 처리하므로 여기서는 null 로 저장.
            translationRepository.save(
                    DisasterAlertTranslation.of(alertId, targetLang, translatedMessage, translatedType, null)
            );
            log.info("번역 완료: alertId={}, lang={}", alertId, targetLang);

        } catch (Exception e) {
            log.warn("번역 실패: alertId={}, lang={}, reason={}", alertId, language.getDbCode(), e.getMessage());
            throw e; // 호출 측(batch)에서 단건 실패를 잡을 수 있도록 재던짐
        }
    }
}
