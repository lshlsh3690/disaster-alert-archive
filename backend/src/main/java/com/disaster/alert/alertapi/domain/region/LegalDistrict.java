package com.disaster.alert.alertapi.domain.region;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

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
}