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

    // V117에서 GENERATED ALWAYS ... STORED로 정의됨 — name이 바뀌면 Postgres가 자동 재계산한다.
    // 애플리케이션이 값을 쓰면 안 되므로 insertable/updatable을 막는다.
    @Column(name = "sigungu_name", insertable = false, updatable = false)
    private String sigunguName;

    // V118에서 GENERATED ALWAYS ... STORED로 정의됨(name과 값은 동일, 컬럼 타입만 COLLATE "C").
    // name 그대로 GROUP BY/ORDER BY하면 DB 기본 collation(en_US.utf8, 로케일 인식 비교)
    // 때문에 정렬 비용이 크다(countByRegion 실측 4,921ms→이 컬럼으로 108ms) — 컬럼 자체에
    // collation이 박혀있어 쿼리 쪽엔 특별한 표현식이 필요 없다. name 컬럼의 collation은
    // 그대로라 다른 화면의 정렬에는 영향이 없다.
    @Column(name = "name_collate_c", insertable = false, updatable = false)
    private String nameCollateC;

    //정적 팩토리: id(=code)만 채운 참조용 엔티티 생성. DB 조회 없음.
    public static LegalDistrict referencedByCode(String code) {
        LegalDistrict ld = new LegalDistrict();
        ld.code = code;           // 클래스 내부라 private 필드에 직접 접근 가능
        return ld;                // name/isActive 등은 null/기본값이므로 접근 금지(링크용으로만 사용)
    }
}