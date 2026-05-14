package com.disaster.alert.alertapi.domain.legaldistrict.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 법정동 코드별 다국어 번역명 엔티티.
 *
 * <p><b>목적</b>
 * <ul>
 *   <li>DeepL 호출 비용 절감 — 재난문자의 법정동 이름을 매번 번역하지 않고 캐시처럼 사용</li>
 *   <li>번역 일관성 — 같은 법정동은 항상 같은 번역명 사용</li>
 * </ul>
 *
 * <p><b>데이터 예시</b>
 * <pre>
 * (code, language_code, name)
 * ('1100000000', 'EN', 'Seoul')
 * ('1100000000', 'JA', 'ソウル特別市')
 * ('1111000000', 'EN', 'Seoul Jongno-gu')
 * </pre>
 *
 * <p><b>참고 패턴</b>: {@code DisasterAlertTranslation} 과 동일한 구조
 * (alertId 대신 법정동 code 를 사용하는 차이만 있음).
 */
@Entity
@Table(name = "legal_district_translation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 용. 외부에서 빈 객체 생성 방지
@AllArgsConstructor
@Builder
public class LegalDistrictTranslation {

    @EmbeddedId
    private LegalDistrictTranslationId id;

    /** 해당 언어로 번역된 법정동 이름 */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * 법정동 마스터(legal_district) 참조.
     *
     * <p>{@code insertable=false, updatable=false} 인 이유:
     * code 컬럼은 이미 {@link LegalDistrictTranslationId#getCode()} 가 관리하므로
     * 이쪽 {@code @ManyToOne} 은 읽기 전용으로만 사용한다.
     *
     * <p>{@code FetchType.LAZY} — 불필요한 JOIN 방지. legalDistrict 정보가
     * 필요한 경우에만 추가 쿼리 발생.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "code", insertable = false, updatable = false)
    private LegalDistrict legalDistrict;

    /**
     * 정적 팩토리. 외부에서 객체 생성 시 이 메서드를 사용.
     * Builder 를 직접 호출하지 않도록 진입점을 단순화한다.
     */
    public static LegalDistrictTranslation of(String code, String languageCode, String name) {
        return LegalDistrictTranslation.builder()
                .id(new LegalDistrictTranslationId(code, languageCode))
                .name(name)
                .build();
    }
}
