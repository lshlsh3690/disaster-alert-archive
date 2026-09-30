package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.model.DisasterEventTranslation;
import com.disaster.alert.alertapi.domain.event.model.DisasterEventTranslationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DisasterEventTranslationRepository
        extends JpaRepository<DisasterEventTranslation, DisasterEventTranslationId> {

    Optional<DisasterEventTranslation> findByIdEventIdAndIdLanguageCode(Long eventId, String languageCode);

    List<DisasterEventTranslation> findByIdEventIdInAndIdLanguageCode(List<Long> eventIds, String languageCode);

    /**
     * 이벤트 제목 번역 캐시 저장 — {@code disaster_alert_translation} 과 동일한 복합 PK +
     * check-then-act 패턴이라 같은 동시성 위험이 있다
     * ({@code DisasterAlertTranslationRepository.upsert} 참고). 확인과 저장을 원자적 단일 SQL
     * 문으로 묶어 그 틈을 없앤다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO disaster_event_translation
                (disaster_event_id, language_code, translated_title, translated_at)
            VALUES (:eventId, :languageCode, :translatedTitle, now())
            ON CONFLICT (disaster_event_id, language_code) DO NOTHING
            """, nativeQuery = true)
    void upsert(@Param("eventId") Long eventId,
                @Param("languageCode") String languageCode,
                @Param("translatedTitle") String translatedTitle);
}
