package com.disaster.alert.alertapi.domain.legaldistrict.repository;

import com.disaster.alert.alertapi.domain.legaldistrict.model.LegalDistrictTranslation;
import com.disaster.alert.alertapi.domain.legaldistrict.model.LegalDistrictTranslationId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 법정동 번역 레포지토리.
 *
 * <p><b>메서드 명명 규칙</b>: {@code findByIdXxx} 형태는 복합 PK 의 필드를 가리킨다.
 * 예: {@code findByIdCodeAndIdLanguageCode} → {@code id.code} 와 {@code id.languageCode} 로 조회.
 *
 * <p>왜 이렇게 쓰는가? {@code @EmbeddedId} 를 사용하면 PK 필드들이
 * {@code id} 라는 객체 안에 묶여 있기 때문에 Spring Data JPA 가
 * 메서드 이름으로 쿼리를 만들 때 {@code Id} 접두사로 진입해야 한다.
 */
public interface LegalDistrictTranslationRepository
        extends JpaRepository<LegalDistrictTranslation, LegalDistrictTranslationId> {

    /**
     * 단건 조회 — 특정 법정동의 특정 언어 번역을 가져온다.
     *
     * @param code 법정동 코드 (예: "1100000000")
     * @param languageCode 언어 코드 (예: "EN")
     */
    Optional<LegalDistrictTranslation> findByIdCodeAndIdLanguageCode(
            String code, String languageCode);

    /**
     * 일괄 조회 — 재난문자 1건이 보유한 여러 법정동을 한 번의 쿼리로 가져온다.
     *
     * <p>한 번에 N개의 법정동 이름을 번역해야 할 때 사용 (N+1 방지).
     *
     * @param codes 법정동 코드 리스트
     * @param languageCode 언어 코드
     * @return 매칭된 번역 엔티티 리스트 (없는 코드는 결과에서 빠짐 — 호출 측 fallback 필요)
     */
    List<LegalDistrictTranslation> findByIdCodeInAndIdLanguageCode(
            List<String> codes, String languageCode);

    /**
     * 존재 여부 확인 — 특정 법정동의 특정 언어 번역이 이미 저장돼 있는지 판단.
     *
     * <p>현재 구현에서는 {@link #findByIdCodeInAndIdLanguageCode} 로 일괄 조회 후
     * Map 에서 누락 여부를 확인하는 방식을 사용한다.
     * 단건 존재 여부 확인이 필요한 경우 이 메서드를 사용할 수 있다.
     */
    boolean existsByIdCodeAndIdLanguageCode(String code, String languageCode);
}
