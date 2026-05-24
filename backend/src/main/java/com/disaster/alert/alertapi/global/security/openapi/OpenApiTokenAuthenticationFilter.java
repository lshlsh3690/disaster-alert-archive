package com.disaster.alert.alertapi.global.security.openapi;

import com.disaster.alert.alertapi.domain.openapi.service.OpenApiTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * OpenAPI 데이터 조회 요청의 서비스키를 검증하는 필터.
 *
 * <p>일반 사용자 JWT와 별개로 외부 클라이언트가 발급받은 OpenAPI 서비스키를 검증한다.
 * 토큰 관리 API는 JWT 인증을 사용해야 하므로 이 필터에서 제외한다.
 */
@Slf4j
@RequiredArgsConstructor
public class OpenApiTokenAuthenticationFilter extends OncePerRequestFilter {
    /** OpenAPI 경로 루트. 하위 경로를 모두 처리한다. */
    private static final String OPEN_API_ROOT = "/api/v1/open-api";

    /** 서비스키 발급/조회/폐기 경로. 이 경로는 JWT 인증 체인이 담당한다. */
    private static final String TOKEN_MANAGEMENT_PREFIX = "/api/v1/open-api/tokens";

    /** 외부 클라이언트가 서비스키를 전달할 때 사용하는 전용 헤더. */
    private static final String OPEN_API_TOKEN_HEADER = "X-OpenAPI-Token";

    /** Bearer 방식도 지원하기 위한 표준 인증 헤더. */
    private static final String AUTHORIZATION_HEADER = "Authorization";

    /** Authorization 헤더에서 서비스키 부분만 분리하기 위한 prefix. */
    private static final String BEARER_PREFIX = "Bearer ";

    /** 서비스키 해시 검증과 마지막 사용 시각 갱신을 담당하는 서비스. */
    private final OpenApiTokenService openApiTokenService;

    /**
     * OpenAPI 데이터 조회 요청에만 필터를 적용한다.
     *
     * <p>토큰 관리 경로는 로그인 JWT 인증이 필요하므로 이 필터를 건너뛴다.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        boolean isOpenApiPath = uri.equals(OPEN_API_ROOT) || uri.startsWith(OPEN_API_ROOT + "/");
        boolean isTokenManagementPath = uri.startsWith(TOKEN_MANAGEMENT_PREFIX);
        return !isOpenApiPath || isTokenManagementPath;
    }

    /**
     * 서비스키를 검증하고 마지막 사용 시각 갱신을 시도한다.
     *
     * <ul>
     *   <li>검증 실패 → 401 반환</li>
     *   <li>검증 성공 + 갱신 실패 → 200 (통계 실패가 인증을 막지 않음)</li>
     *   <li>검증 성공 + 갱신 성공 → 200</li>
     * </ul>
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);

        if (!openApiTokenService.validate(token)) {
            unauthorized(response);
            return;
        }

        try {
            openApiTokenService.markUsed(token);
        } catch (Exception e) {
            log.warn("lastUsedAt 갱신 실패 - 인증은 통과 처리: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /** X-OpenAPI-Token 헤더를 우선 사용하고, 없으면 Authorization Bearer 값을 사용한다. */
    private String resolveToken(HttpServletRequest request) {
        String token = request.getHeader(OPEN_API_TOKEN_HEADER);
        if (token != null && !token.isBlank()) {
            return token.trim();
        }

        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }

        return null;
    }

    /** 서비스키 인증 실패 응답을 작성한다. */
    private void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Invalid or expired OpenAPI token\"}");
    }
}
