package com.disaster.alert.alertapi.domain.disasteralert.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 재난문자 발신 기관(specs/007 R11). 본문 끝 대괄호 표기에서 자동 등록되며, 같은 기관의 여러 표기는
 * {@link AgencyAlias} 가 이 기관 하나로 모은다. 이름은 유일하지 않다(정규화 뒤 합쳐질 수 있다).
 */
@Entity
@Table(name = "agency")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Agency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private Agency(String name) {
        this.name = name;
        this.createdAt = LocalDateTime.now();
    }

    /** 처음 본 표기를 이름으로 하는 기관을 만든다(저장은 호출한 쪽 몫). */
    public static Agency named(String name) {
        return new Agency(name);
    }
}
