package com.disaster.alert.alertapi.domain.disasteralert.repository;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlertTranslation;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlertTranslationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DisasterAlertTranslationRepository
        extends JpaRepository<DisasterAlertTranslation, DisasterAlertTranslationId> {

    /** 단건 조회 — 상세 조회 시 사용 */
    Optional<DisasterAlertTranslation> findByIdAlertIdAndIdLanguageCode(Long alertId, String languageCode);

    /** 일괄 조회 — 목록 조회 시 한 번에 가져오기 (lazy 번역에서 누락 식별용) */
    List<DisasterAlertTranslation> findByIdAlertIdInAndIdLanguageCode(List<Long> alertIds, String languageCode);

    /**
     * 번역 캐시 저장 — check-then-act(존재 확인 → save()) 대신 확인과 저장을 DB 한 문장으로 묶는다.
     * {@code save()}는 {@code @EmbeddedId} 복합키라 INSERT 를 즉시 실행하지 않고 예약만 하므로
     * (쓰기 지연), 두 요청이 같은 (alertId, languageCode) 를 거의 동시에 저장하면 그 사이 틈에서
     * PK 충돌이 나 호출부 try/catch 밖에서 터진다(회귀 테스트 참고). ON CONFLICT DO NOTHING 은
     * 확인과 저장을 원자적 단일 SQL 문으로 만들어 그 틈 자체를 없앤다 — 먼저 저장된 쪽을 유지하고
     * 뒤늦게 도착한 쪽은 조용히 무시한다(같은 원문을 같은 언어로 번역한 결과이므로 내용은 동일).
     */
    @Modifying
    @Query(value = """
            INSERT INTO disaster_alert_translation
                (disaster_alert_id, language_code, translated_message,
                 translated_disaster_type, translated_region_names, translated_at)
            VALUES (:alertId, :languageCode, :translatedMessage, :translatedDisasterType, NULL, now())
            ON CONFLICT (disaster_alert_id, language_code) DO NOTHING
            """, nativeQuery = true)
    void upsert(@Param("alertId") Long alertId,
                @Param("languageCode") String languageCode,
                @Param("translatedMessage") String translatedMessage,
                @Param("translatedDisasterType") String translatedDisasterType);
}
