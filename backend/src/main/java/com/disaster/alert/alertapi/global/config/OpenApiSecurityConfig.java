package com.disaster.alert.alertapi.global.config;

import com.disaster.alert.alertapi.global.security.jwt.JwtAuthenticationFilter;
import com.disaster.alert.alertapi.global.security.openapi.OpenApiTokenAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * OpenAPI 전용 Spring Security 설정.
 *
 * <p>OpenAPI 기능은 두 가지 인증 방식을 사용한다.
 * 서비스키 관리 API는 로그인 JWT가 필요하고, 실제 데이터 조회 API는 OpenAPI 서비스키가 필요하다.
 * 따라서 기존 일반 서비스 보안 체인과 분리해 각 경로의 인증 책임을 명확히 했다.
 */
@Configuration
@RequiredArgsConstructor
public class OpenApiSecurityConfig {
    /** 서비스키 관리 API에서 로그인 사용자를 인증하기 위한 기존 JWT 필터. */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /** OpenAPI 데이터 조회 API에서 서비스키를 검증하는 필터. */
    private final OpenApiTokenAuthenticationFilter openApiTokenAuthenticationFilter;

    /** 기존 SecurityConfig와 동일한 CORS 정책을 재사용한다. */
    private final CorsConfigurationSource corsConfigurationSource;

    /**
     * 서비스키 관리 API 보안 체인.
     *
     * <p>/api/v1/open-api/tokens/** 는 로그인한 회원이 자신의 서비스키를 관리하는 경로이므로 JWT 인증을 적용한다.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain openApiTokenManagementFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/v1/open-api/tokens/**")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"message\":\"UNAUTHORIZED\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"message\":\"FORBIDDEN\"}");
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * OpenAPI 데이터 조회 보안 체인.
     *
     * <p>/api/v1/open-api/** 중 토큰 관리 경로를 제외한 요청은 서비스키 인증 필터에서 검증한다.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain openApiDataFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/v1/open-api/**")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(openApiTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
