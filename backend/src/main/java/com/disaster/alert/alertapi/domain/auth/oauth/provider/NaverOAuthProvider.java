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
public class NaverOAuthProvider implements OAuthProvider {
    private final OAuthProperties properties;

    private static final String AUTHORIZE_URI = "https://nid.naver.com/oauth2.0/authorize";
    private static final String TOKEN_URI = "https://nid.naver.com/oauth2.0/token";
    private static final String USERINFO_URI = "https://openapi.naver.com/v1/nid/me";

    @Override
    public String getProviderKey() {
        return "naver";
    }

    @Override
    public String buildAuthorizeUrl(String state) {
        OAuthProperties.Provider p = properties.getNaver();
        if (p == null || p.getClientId() == null || p.getRedirectUri() == null) {
            throw new IllegalStateException("Missing OAuth naver configuration (clientId/redirectUri)");
        }
        return UriComponentsBuilder.fromHttpUrl(AUTHORIZE_URI)
                .queryParam("response_type", "code")
                .queryParam("client_id", p.getClientId())
                .queryParam("redirect_uri", p.getRedirectUri())
                .queryParamIfPresent("state", state == null || state.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(state))
                .build(true)
                .toUriString();
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuthMemberInfo fetchMemberInfo(String code, String state) {
        OAuthProperties.Provider p = properties.getNaver();

        RestClient client = RestClient.create();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", p.getClientId());
        form.add("client_secret", p.getClientSecret());
        form.add("code", code);
        if (state != null) form.add("state", state);

        Map<String, Object> tokenRes = client.post()
                .uri(TOKEN_URI)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

        String accessToken = tokenRes.get("access_token").toString();

        Map<String, Object> userWrap = client.get()
                .uri(USERINFO_URI)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);

        Map<String, Object> response = (Map<String, Object>) userWrap.get("response");
        String providerUserId = response != null ? (String) response.get("id") : null;
        String email = response != null ? (String) response.get("email") : null;
        String nickname = response != null ? (String) response.getOrDefault("nickname", email) : email;
        return new OAuthMemberInfo(getProviderKey(), providerUserId, email, nickname);
    }

    private String url(String v){
        return v == null ? "" : v.replace(" " , "%20");
    }
}


