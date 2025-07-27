package com.disaster.alert.alertapi.domain.member.dto;


import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignUpRequest {
    private String email;
    private String password;
    private String nickname;
    private String name;
}
