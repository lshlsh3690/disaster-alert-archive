package com.disaster.alert.alertapi.domain.member.service;

import com.disaster.alert.alertapi.domain.common.exception.CustomException;
import com.disaster.alert.alertapi.domain.common.exception.ErrorCode;
import com.disaster.alert.alertapi.domain.legaldistrict.repository.LegalDistrictRepository;
import com.disaster.alert.alertapi.domain.member.dto.MemberFavoriteRegionDtos;
import com.disaster.alert.alertapi.domain.member.model.MemberFavoriteRegion;
import com.disaster.alert.alertapi.domain.member.model.MemberRole;
import com.disaster.alert.alertapi.domain.member.repository.MemberFavoriteRegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberFavoriteRegionService {

    private static final int USER_MAX_FAVORITE_REGION_COUNT = 5;

    private final MemberFavoriteRegionRepository memberFavoriteRegionRepository;
    private final LegalDistrictRepository legalDistrictRepository;

    @Transactional(readOnly = true)
    public List<MemberFavoriteRegionDtos.Response> getMyFavoriteRegions(Long memberId) {
        return memberFavoriteRegionRepository.findByIdMemberId(memberId)
                .stream()
                .map(MemberFavoriteRegionDtos.Response::from)
                .toList();
    }

    @Transactional
    public void addFavoriteRegion(Long memberId, MemberRole role, String legalDistrictCode) {
        if (!legalDistrictRepository.existsByCode(legalDistrictCode)) {
            throw new CustomException(ErrorCode.LEGAL_DISTRICT_NOT_FOUND);
        }
        if (memberFavoriteRegionRepository.existsByIdMemberIdAndIdLegalDistrictCode(memberId, legalDistrictCode)) {
            throw new CustomException(ErrorCode.FAVORITE_REGION_ALREADY_EXISTS);
        }
        int limit = getLimitByRole(role);
        if (limit != Integer.MAX_VALUE && memberFavoriteRegionRepository.countByIdMemberId(memberId) >= limit) {
            throw new CustomException(ErrorCode.FAVORITE_REGION_LIMIT_EXCEEDED);
        }
        memberFavoriteRegionRepository.save(MemberFavoriteRegion.of(memberId, legalDistrictCode));
    }

    private int getLimitByRole(MemberRole role) {
        return switch (role) {
            case ADMIN -> Integer.MAX_VALUE;
            default -> USER_MAX_FAVORITE_REGION_COUNT;
        };
    }

    @Transactional
    public void deleteFavoriteRegion(Long memberId, String legalDistrictCode) {
        if (!memberFavoriteRegionRepository.existsByIdMemberIdAndIdLegalDistrictCode(memberId, legalDistrictCode)) {
            throw new CustomException(ErrorCode.FAVORITE_REGION_NOT_FOUND);
        }
        memberFavoriteRegionRepository.deleteByIdMemberIdAndIdLegalDistrictCode(memberId, legalDistrictCode);
    }
}