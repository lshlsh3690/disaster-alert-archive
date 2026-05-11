package com.disaster.alert.alertapi.domain.legaldistrict.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * LegalDistrictTranslation 의 복합 PK 클래스.
 *
 * <p>한 법정동(code)에 대해 언어별로 1개의 번역만 존재하므로
 * (code, language_code) 조합을 기본키로 사용한다.
 *
 * <p>JPA 명세상 복합 PK 클래스는 다음 조건을 충족해야 한다:
 * <ul>
 *   <li>{@code Serializable} 구현 — 2차 캐시/분산 환경 대비</li>
 *   <li>{@code equals()} / {@code hashCode()} 구현 — 영속성 컨텍스트에서 동일성 비교에 사용</li>
 *   <li>기본 생성자 — JPA 리플렉션용</li>
 * </ul>
 *
 * <p>{@code @Setter} 를 두지 않은 이유: PK 는 한번 생성되면 변경되어서는 안 되기 때문.
 * (MemberFavoriteRegionId 도 동일한 이유로 Setter 제거됨)
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class LegalDistrictTranslationId implements Serializable {

    /** 법정동 코드 (예: "1100000000" = 서울특별시) */
    @Column(name = "code")
    private String code;

    /** 언어 코드 (예: "EN", "JA", "ZH") */
    @Column(name = "language_code")
    private String languageCode;
}
