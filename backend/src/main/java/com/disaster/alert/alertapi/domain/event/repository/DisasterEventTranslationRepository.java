package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.model.DisasterEventTranslation;
import com.disaster.alert.alertapi.domain.event.model.DisasterEventTranslationId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DisasterEventTranslationRepository
        extends JpaRepository<DisasterEventTranslation, DisasterEventTranslationId> {

    Optional<DisasterEventTranslation> findByIdEventIdAndIdLanguageCode(Long eventId, String languageCode);

    List<DisasterEventTranslation> findByIdEventIdInAndIdLanguageCode(List<Long> eventIds, String languageCode);
}
