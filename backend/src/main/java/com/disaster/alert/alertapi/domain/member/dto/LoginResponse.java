package com.disaster.alert.alertapi.domain.member.dto;

import com.disaster.alert.alertapi.domain.member.model.Member;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private Long memberId;
    private String nickname;
    private String email;

    public static LoginResponse of(Member member, String accessToken, String refreshToken) {
        return LoginResponse.builder()
                .memberId(member.getId())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}