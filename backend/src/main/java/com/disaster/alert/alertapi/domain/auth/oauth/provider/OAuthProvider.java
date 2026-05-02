package com.disaster.alert.alertapi.domain.auth.oauth.provider;

import com.disaster.alert.alertapi.domain.auth.oauth.OAuthMemberInfo;

public interface OAuthProvider {
    String getProviderKey();
    String buildAuthorizeUrl(String state);
    OAuthMemberInfo fetchMemberInfo(String code, String state);
}


