package com.disaster.alert.alertapi.domain.disasteralert.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 본문에서 뽑은 발신 기관 표기(공백 포함, 가공 없음) → 기관 하나. 표기 자체가 PK 다.
 * 정규화로 표기를 다른 기관에 다시 연결할 수 있도록 기관과 분리해 둔다.
 */
@Entity
@Table(name = "agency_alias")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgencyAlias {

    @Id
    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private AgencyAlias(String label, Agency agency) {
        this.label = label;
        this.agency = agency;
        this.createdAt = LocalDateTime.now();
    }

    /** 표기가 기관을 가리키게 한다(저장은 호출한 쪽 몫). */
    public static AgencyAlias of(String label, Agency agency) {
        return new AgencyAlias(label, agency);
    }
}
