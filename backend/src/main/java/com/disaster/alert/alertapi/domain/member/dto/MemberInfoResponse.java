package com.disaster.alert.alertapi.domain.member.dto;


import com.disaster.alert.alertapi.domain.member.model.Member;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MemberInfoResponse {
    private Long id;
    private String email;
    private String nickname;
    private String role;

    public static MemberInfoResponse from(Member member) {
        return new MemberInfoResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getRole().name()
        );
    }
}

