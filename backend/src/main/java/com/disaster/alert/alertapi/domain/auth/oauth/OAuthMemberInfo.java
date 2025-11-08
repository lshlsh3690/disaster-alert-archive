package com.disaster.alert.alertapi.domain.auth.oauth;

public record OAuthMemberInfo(
        String provider,
        String providerUserId,
        String email,
        String nickname
) {
}


