package com.disaster.alert.alertapi.domain.member.dto;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignUpRequest {
    @Email(message = "유효한 이메일 형식이 아닙니다.")
    @NotBlank(message = "이메일은 필수 입력 항목입니다.")
    private String email;
    @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
    private String password;
    @NotBlank(message = "비밀번호 확인은 필수 입력 항목입니다.")
    private String confirmPassword;
    @NotBlank(message = "닉네임은 필수 입력 항목입니다.")
    private String nickname;

    public void validatePasswordMatch() {
        if (!password.equals(confirmPassword)) {
            throw new IllegalArgumentException("비밀번호와 비밀번호 확인이 일치하지 않습니다.");
        }
    }
}
