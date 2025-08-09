package com.disaster.alert.alertapi.domain.member.dto;

import com.disaster.alert.alertapi.domain.member.model.Member;
import com.disaster.alert.alertapi.domain.member.model.MemberRole;

public record MemberResponse(
        Long id,
        String email,
        String nickname,
        MemberRole role
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getRole()
        );
    }
}