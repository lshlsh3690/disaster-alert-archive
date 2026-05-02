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
public class GoogleOAuthProvider implements OAuthProvider {
    private final OAuthProperties properties;

    private static final String AUTHORIZE_URI = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URI = "https://www.googleapis.com/oauth2/v2/userinfo";

    @Override
    public String getProviderKey() {
        return "google";
    }

    @Override
    public String buildAuthorizeUrl(String state) {
        OAuthProperties.Provider p = properties.getGoogle();
        if (p == null || p.getClientId() == null || p.getRedirectUri() == null) {
            throw new IllegalStateException("Missing OAuth google configuration (clientId/redirectUri)");
        }
        return UriComponentsBuilder.fromHttpUrl(AUTHORIZE_URI)
                .queryParam("client_id", p.getClientId())
                .queryParam("redirect_uri", p.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParamIfPresent("state", state == null || state.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(state))
                .build()
                .encode()
                .toUriString();
    }

    @Override
    public OAuthMemberInfo fetchMemberInfo(String code, String state) {
        OAuthProperties.Provider p = properties.getGoogle();

        RestClient client = RestClient.create();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", p.getClientId());
        form.add("client_secret", p.getClientSecret());
        form.add("redirect_uri", p.getRedirectUri());
        form.add("grant_type", "authorization_code");

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

        String providerUserId = String.valueOf(user.get("id")); // v2 userinfo returns 'id'
        String email = (String) user.get("email");
        String nickname = (String) user.getOrDefault("name", email);
        return new OAuthMemberInfo(getProviderKey(), providerUserId, email, nickname);
    }

    private String url(String v){
        return v == null ? "" : v.replace(" ", "%20");
    }
}


