package com.disaster.alert.alertapi.global.security.jwt;

import com.disaster.alert.alertapi.domain.member.service.CustomUserDetailsService;
import com.disaster.alert.alertapi.global.redis.RedisService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final RedisService redisService;

    // 모든 요청에 대해 필터는 지나가되, 토큰이 없으면 바로 다음 체인으로 넘깁니다.

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        log.info("JWT Authentication Filter invoked");

        String token = resolveToken(request);

        //토큰이 없으면 그냥 다음 필터로 넘김 (로그인/회원가입/공개 API가 동작해야 함)
        if (token == null || token.isBlank()) {
            log.info("토큰이 없습니다. 다음 필터로 이동합니다.");
            filterChain.doFilter(request, response);
            return;
        }


        if (redisService.isBlackListToken(token)) {
            log.warn("블랙리스트에 등록된 토큰입니다.");
            unauthorized(response, "Access token is blacklisted");
            return;
        }

        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("유효하지 않은 토큰입니다.");
            unauthorized(response, "Invalid or expired access token");
            return;
        }
        Claims claims = jwtTokenProvider.getClaims(token);
        String email = claims.getSubject();

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        log.info(userDetails.toString());

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (request.getCookies() != null) {
            for (Cookie c : cookies) {
                if ("accessToken".equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                    return c.getValue();
                }
            }
        }
        return null;
    }

    private void unauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + msg + "\"}");
    }
}