package com.disaster.alert.alertapi.domain.member.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Setter
public class MemberFavoriteRegionId implements Serializable {

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "legal_district_code")
    private String legalDistrictCode;
}
