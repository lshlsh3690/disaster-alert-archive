package com.disaster.alert.alertapi.domain.member.dto;

import jakarta.validation.constraints.NotBlank;

public record MemberUpdateRequest(
        @NotBlank
        String name,
        @NotBlank
        String nickname
) {}

