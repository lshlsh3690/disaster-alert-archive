package com.disaster.alert.alertapi.domain.auth.oauth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/oauth")
public class OAuthController {
    private final OAuthService oAuthService;

    @GetMapping("/{provider}/authorize")
    public ResponseEntity<Void> authorize(@PathVariable("provider") String provider,
                                          @RequestParam(value = "redirect", required = false) String redirect) {
        String url = oAuthService.buildAuthorizeUrl(provider, redirect);
        return ResponseEntity.status(302).header("Location", url).build();
    }

    @GetMapping("/{provider}/callback")
    public ResponseEntity<Void> callback(@PathVariable("provider") String provider,
                                         @RequestParam("code") String code,
                                         @RequestParam(value = "state", required = false) String state) {
        var result = oAuthService.handleCallback(provider, code, state);

        String successRedirect = result.isNew()
                ? oAuthService.buildOnboardingRedirect(state)
                : oAuthService.getSuccessRedirectOrDefault(state);

        return ResponseEntity.status(302)
                .header("Set-Cookie", result.accessCookie().toString())
                .header("Set-Cookie", result.refreshCookie().toString())
                .header("Location", successRedirect)
                .build();
    }
}


