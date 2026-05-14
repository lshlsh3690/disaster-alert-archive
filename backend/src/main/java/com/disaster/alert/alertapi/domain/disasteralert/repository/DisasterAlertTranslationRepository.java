package com.disaster.alert.alertapi.domain.disasteralert.repository;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlertTranslation;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlertTranslationId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DisasterAlertTranslationRepository
        extends JpaRepository<DisasterAlertTranslation, DisasterAlertTranslationId> {

    /** 단건 조회 — 상세 조회 시 사용 */
    Optional<DisasterAlertTranslation> findByIdAlertIdAndIdLanguageCode(Long alertId, String languageCode);

    /** 일괄 조회 — 목록 조회 시 한 번에 가져오기 (lazy 번역에서 누락 식별용) */
    List<DisasterAlertTranslation> findByIdAlertIdInAndIdLanguageCode(List<Long> alertIds, String languageCode);
}
