package com.disaster.alert.alertapi.domain.auth.oauth.provider;

import com.disaster.alert.alertapi.domain.auth.oauth.OAuthMemberInfo;
import com.disaster.alert.alertapi.domain.auth.oauth.OAuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class KakaoOAuthProvider implements OAuthProvider {
    private final OAuthProperties properties;

    private static final String AUTHORIZE_URI = "https://kauth.kakao.com/oauth/authorize";
    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USERINFO_URI = "https://kapi.kakao.com/v2/user/me";

    @Override
    public String getProviderKey() {
        return "kakao";
    }

    @Override
    public String buildAuthorizeUrl(String state) {
        OAuthProperties.Provider p = properties.getKakao();
        if (p == null || p.getClientId() == null || p.getRedirectUri() == null) {
            throw new IllegalStateException("Missing OAuth kakao configuration (clientId/redirectUri)");
        }
        return UriComponentsBuilder.fromHttpUrl(AUTHORIZE_URI)
                .queryParam("client_id", p.getClientId())
                .queryParam("redirect_uri", p.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParamIfPresent("state", state == null || state.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(state))
                .build(true)
                .toUriString();
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuthMemberInfo fetchMemberInfo(String code, String state) {
        OAuthProperties.Provider p = properties.getKakao();

        RestClient client = RestClient.create();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", p.getClientId());
        form.add("client_secret", p.getClientSecret());
        form.add("redirect_uri", p.getRedirectUri());
        form.add("code", code);

        Map<String, Object> tokenRes = client.post()
                .uri(TOKEN_URI)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

        String accessToken = tokenRes.get("access_token").toString();

        Map<String, Object> user = client.get()
                .uri(USERINFO_URI)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);

        // kakao_account.email, profile.nickname
        String providerUserId = String.valueOf(user.get("id"));
        Map<String, Object> kakaoAccount = (Map<String, Object>) user.get("kakao_account");
        String email = kakaoAccount != null ? (String) kakaoAccount.get("email") : null;

        Map<String, Object> profile = kakaoAccount != null ? (Map<String, Object>) kakaoAccount.get("profile") : null;
        if (profile == null) {
            profile = (Map<String, Object>) user.get("properties");
        }
        String nickname = profile != null ? (String) profile.getOrDefault("nickname", email) : email;

        return new OAuthMemberInfo(getProviderKey(), providerUserId, email, nickname);
    }

    private String url(String v){
        return v == null ? "" : v.replace(" ", "%20");
    }
}


