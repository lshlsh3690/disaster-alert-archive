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
     * <b>현재 운영 코드에서 호출하지 않는다.</b> 저장은 {@code EventTranslationService} 의 요청 병합
     * (single-flight)+안쪽 재확인 후 {@code save()} 로 한다. 이 메서드가 남아 있는 이유는
     * {@code EventTranslationServiceTest} 가 "저장 경로가 upsert 로 새지 않는다"를
     * {@code verify(..., never()).upsert(...)} 로 고정하고 있어, 지우면 그 테스트가 컴파일되지 않기
     * 때문이다. 테스트에서 그 단언을 뺄 때 함께 지운다.
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
