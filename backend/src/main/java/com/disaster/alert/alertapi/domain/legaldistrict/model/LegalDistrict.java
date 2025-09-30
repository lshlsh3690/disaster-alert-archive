package com.disaster.alert.alertapi.domain.legaldistrict.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "legal_district")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class LegalDistrict {
    @Id
    @Column(name = "code", length = 10)
    private String code;        // 예: 1111010200

    @Column(name = "name", nullable = false)
    private String name;        // 예: 서울특별시 종로구 신교동

    @Column(name = "is_active", nullable = false)
    private boolean isActive;   // true: 존재 / false: 폐지

    @Column(name = "is_active_string", nullable = false)
    private String isActiveString;

    //정적 팩토리: id(=code)만 채운 참조용 엔티티 생성. DB 조회 없음.
    public static LegalDistrict referencedByCode(String code) {
        LegalDistrict ld = new LegalDistrict();
        ld.code = code;           // 클래스 내부라 private 필드에 직접 접근 가능
        return ld;                // name/isActive 등은 null/기본값이므로 접근 금지(링크용으로만 사용)
    }
}