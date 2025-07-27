package com.disaster.alert.alertapi.domain.member.dto;

import com.disaster.alert.alertapi.domain.member.model.Member;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.stream.Collectors;

@Getter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class LoginResponse {
    private String accessToken;
    private Long memberId;
    private String email;

    public static LoginResponse of(Member member, String accessToken, String refreshToken) {
        return LoginResponse.builder()
                .memberId(member.getId())
                .email(member.getEmail())
                .accessToken(accessToken)
                .build();
    }
}