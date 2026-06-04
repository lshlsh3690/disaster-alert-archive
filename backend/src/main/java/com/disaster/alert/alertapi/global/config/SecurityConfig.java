package com.disaster.alert.alertapi.global.config;

import com.disaster.alert.alertapi.domain.member.service.CustomUserDetailsService;
import com.disaster.alert.alertapi.global.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    private final CustomUserDetailsService userDetailsService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    /** OpenAPI 전용 체인 이후에 적용되는 일반 서비스 API 보안 체인. */
    @Order(3)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/members/me").authenticated()
                        .requestMatchers("/api/v1/members/check-nickname").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/user-alerts/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/comments/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/community/**").permitAll()
                        .requestMatchers("/api/v1/alerts/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/**").permitAll()
                        .requestMatchers("/api/v1/districts/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").permitAll() 
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                //401 403에 토큰을 재발급할수 있도록 메세지 리턴해줌
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            log.info(authException.getMessage());
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
                            response.setContentType("application/json");
                            response.getWriter().write("{\"message\":\"UNAUTHORIZED\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            log.info(accessDeniedException.getMessage());
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
                            response.setContentType("application/json");
                            response.getWriter().write("{\"message\":\"FORBIDDEN\"}");
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authenticationManager(authenticationManager())
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(passwordEncoder);
        authenticationProvider.setUserDetailsService(userDetailsService);
        return new ProviderManager(List.of(authenticationProvider));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(List.of(
                "https://*.vercel.app",
                "http://localhost:3000",
                "https://www.disaster-alert-archive.co.kr",
                "https://disaster-alert-archive.co.kr"

        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
