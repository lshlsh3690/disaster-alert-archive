package com.disaster.alert.alertapi.domain.auth.oauth;

import com.disaster.alert.alertapi.domain.member.model.Member;
import com.disaster.alert.alertapi.domain.member.model.MemberRole;
import com.disaster.alert.alertapi.domain.member.repository.MemberRepository;
import com.disaster.alert.alertapi.domain.member.repository.MemberSocialRepository;
import com.disaster.alert.alertapi.domain.member.model.MemberSocial;
import com.disaster.alert.alertapi.domain.auth.oauth.provider.OAuthProvider;
import com.disaster.alert.alertapi.global.redis.RedisService;
import com.disaster.alert.alertapi.global.security.jwt.JwtTokenProvider;
import com.disaster.alert.alertapi.global.util.CookieUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OAuthService {
    private final List<OAuthProvider> providers;
    private final OAuthProperties oAuthProperties;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisService redisService;
    private final MemberSocialRepository memberSocialRepository;

    @Value("${cookie.secure:true}")
    private boolean isSecureCookie;
    
    @Value("${cookie.domain:}")
    private String cookieDomain;

    private Map<String, OAuthProvider> providerMap() {
        return providers.stream().collect(Collectors.toMap(OAuthProvider::getProviderKey, p -> p));
    }

    public String buildAuthorizeUrl(String providerKey, String state) {
        OAuthProvider provider = getProvider(providerKey);
        return provider.buildAuthorizeUrl(state);
    }

    public OAuthLoginResult handleCallback(String providerKey, String code, String state) {
        OAuthProvider provider = getProvider(providerKey);
        OAuthMemberInfo memberInfo = provider.fetchMemberInfo(code, state);

        if (memberInfo == null || memberInfo.email() == null || memberInfo.email().isBlank()) {
            throw new IllegalStateException("OAuth provider did not return email");
        }

        EnsureResult ensured = ensureMember(memberInfo);
        Member member = ensured.member;

        String accessToken = jwtTokenProvider.generateToken(member.getId(), member.getEmail(), member.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(member.getEmail());
        redisService.saveRefreshToken(member.getEmail(), refreshToken, jwtTokenProvider.getRefreshTokenExpiration() * 1000L);

        ResponseCookie accessCookie = CookieUtil.buildCookie("accessToken", accessToken, Duration.ofHours(1), isSecureCookie, cookieDomain);
        ResponseCookie refreshCookie = CookieUtil.buildCookie("refreshToken", refreshToken, Duration.ofDays(7), isSecureCookie, cookieDomain);

        return new OAuthLoginResult(accessCookie, refreshCookie, member, ensured.created);
    }

    public String getSuccessRedirectOrDefault(String state) {
        if (state != null && !state.isBlank()) return state;
        return Optional.ofNullable(oAuthProperties.getSuccessRedirect()).orElse("/");
    }

    public String buildOnboardingRedirect(String stateOrDefault) {
        String base = getSuccessRedirectOrDefault(stateOrDefault);
        try {
            URI uri = URI.create(base);
            String origin = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
            return origin + "/user/settings?first=1";
        } catch (Exception e) {
            return "/user/settings?first=1";
        }
    }

    private EnsureResult ensureMember(OAuthMemberInfo info) {
        // 1) provider + providerUserId 로 우선 매칭
        if (info.provider() != null && info.providerUserId() != null) {
            Optional<MemberSocial> linked = memberSocialRepository.findByProviderAndProviderUserId(info.provider(), info.providerUserId());
            if (linked.isPresent()) {
                return new EnsureResult(linked.get().getMember(), false);
            }
        }

        // 2) 이메일로 기존 회원 매칭 후 소셜 링크 생성
        if (info.email() != null && !info.email().isBlank()) {
            Optional<Member> byEmail = memberRepository.findByEmail(info.email());
            if (byEmail.isPresent()) {
                linkSocial(byEmail.get(), info);
                return new EnsureResult(byEmail.get(), false);
            }
        }

        // 3) 신규 생성 후 소셜 링크 생성
        Member created = memberRepository.save(
                Member.builder()
                        .email(info.email())
                        .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                        .nickname(info.nickname() != null ? info.nickname() : info.email())
                        .role(MemberRole.USER)
                        .build()
        );
        linkSocial(created, info);
        return new EnsureResult(created, true);
    }

    private void linkSocial(Member member, OAuthMemberInfo info) {
        if (info.provider() == null || info.providerUserId() == null) return;
        MemberSocial link = MemberSocial.builder()
                .member(member)
                .provider(info.provider())
                .providerUserId(info.providerUserId())
                .emailFromProvider(info.email())
                .build();
        try {
            memberSocialRepository.save(link);
        } catch (Exception ignored) {
            // unique 제약 충돌 시 무시 (이미 연결된 경우)
        }
    }

    private OAuthProvider getProvider(String providerKey) {
        OAuthProvider provider = providerMap().get(providerKey);
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported provider: " + providerKey);
        }
        return provider;
    }

    public record OAuthLoginResult(ResponseCookie accessCookie, ResponseCookie refreshCookie, Member member, boolean isNew) {}

    private static class EnsureResult {
        private final Member member;
        private final boolean created;
        private EnsureResult(Member member, boolean created) {
            this.member = member;
            this.created = created;
        }
    }
}


