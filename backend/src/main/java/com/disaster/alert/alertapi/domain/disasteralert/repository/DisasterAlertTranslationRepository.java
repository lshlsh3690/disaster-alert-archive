package com.disaster.alert.alertapi.domain.disasteralert.repository;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlertTranslation;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlertTranslationId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DisasterAlertTranslationRepository extends JpaRepository<DisasterAlertTranslation, DisasterAlertTranslationId> {

    Optional<DisasterAlertTranslation> findByIdAlertIdAndIdLanguageCode(Long alertId, String languageCode);
}
