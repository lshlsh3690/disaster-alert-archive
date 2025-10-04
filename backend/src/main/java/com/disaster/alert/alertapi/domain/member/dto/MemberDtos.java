// src/main/java/com/disaster/alert/alertapi/domain/member/dto/MemberDtos.java
package com.disaster.alert.alertapi.domain.member.dto;

import com.disaster.alert.alertapi.domain.common.exception.CustomException;
import com.disaster.alert.alertapi.domain.common.exception.ErrorCode;
import com.disaster.alert.alertapi.domain.member.model.Member;
import com.disaster.alert.alertapi.domain.member.model.MemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class MemberDtos {
    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateRequest {
        @NotBlank @Email
        private String email;

        @NotBlank @Size(min = 8, max = 64)
        private String password;

        // 프론트와 호환: confirmPassword 유지
        @NotBlank
        private String confirmPassword;

        @NotBlank @Size(min = 2, max = 30)
        private String nickname;

        public void validatePasswordMatch() {
            if (!password.equals(confirmPassword)) {
                throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
            }
        }
    }

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateRequest {
        @NotBlank @Size(min=2, max=30) private String nickname;
    }

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private Long id;
        private String email;
        private String nickname;
        private MemberRole role;

        public static Response from(Member m) {
            return Response.builder()
                    .id(m.getId())
                    .email(m.getEmail())
                    .nickname(m.getNickname())
                    .role(m.getRole())
                    .build();
        }
    }
}
