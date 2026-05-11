package com.disaster.alert.alertapi.domain.member.dto;

import com.disaster.alert.alertapi.domain.member.model.MemberFavoriteRegion;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

public class MemberFavoriteRegionDtos {

    public record AddRequest(@NotBlank String legalDistrictCode) {}

    @Getter
    @AllArgsConstructor
    public static class Response {
        private String legalDistrictCode;
        private String regionName;
        private LocalDateTime createdAt;

        public static Response from(MemberFavoriteRegion mfr) {
            return new Response(
                    mfr.getId().getLegalDistrictCode(),
                    mfr.getLegalDistrict().getName(),
                    mfr.getCreatedAt()
            );
        }
    }
}