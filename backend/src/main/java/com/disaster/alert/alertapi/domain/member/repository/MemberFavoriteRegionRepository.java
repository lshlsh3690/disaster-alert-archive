package com.disaster.alert.alertapi.domain.member.repository;

import com.disaster.alert.alertapi.domain.member.model.MemberFavoriteRegion;
import com.disaster.alert.alertapi.domain.member.model.MemberFavoriteRegionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberFavoriteRegionRepository
        extends JpaRepository<MemberFavoriteRegion, MemberFavoriteRegionId> {

    List<MemberFavoriteRegion> findByIdMemberId(Long memberId);

    long countByIdMemberId(Long memberId);

    boolean existsByIdMemberIdAndIdLegalDistrictCode(Long memberId, String legalDistrictCode);

    void deleteByIdMemberIdAndIdLegalDistrictCode(Long memberId, String legalDistrictCode);

    List<MemberFavoriteRegion> findByIdLegalDistrictCodeIn(List<String> legalDistrictCodes);

    List<MemberFavoriteRegion> findByIdLegalDistrictCode(String legalDistrictCode);
}
