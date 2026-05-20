package com.disaster.alert.alertapi.domain.openapi.dto;

import com.disaster.alert.alertapi.domain.openapi.model.OpenApiToken;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * OpenAPI 서비스키 API에서 사용하는 요청/응답 DTO 모음.
 *
 * <p>기존 프로젝트의 도메인별 Dtos 클래스 패턴에 맞춰 관련 record들을 한 클래스에 묶었다.
 */
public class OpenApiTokenDtos {
    /** 서비스키 발급 요청. 이름과 선택적 만료 기간을 받는다. */
    public record CreateRequest(
            /** 사용자가 서비스키를 구분하기 위해 지정하는 이름. */
            @NotBlank
            @Size(max = 100)
            String name,

            /** 서비스키 유효 기간. 생략하면 기본값을 서비스에서 적용한다. */
            @Min(1)
            @Max(365)
            Integer expiresInDays
    ) {
    }

    /** 서비스키 발급 응답. 원본 token은 발급 직후 한 번만 반환한다. */
    public record CreateResponse(
            /** 발급된 서비스키 식별자. */
            Long id,
            /** 서비스키 이름. */
            String name,
            /** 원본 서비스키. 이후 목록 조회에서는 다시 제공하지 않는다. */
            String token,
            /** 목록에서 식별 가능한 토큰 앞부분. */
            String tokenPrefix,
            /** 서비스키 만료 시각. */
            LocalDateTime expiresAt,
            /** 서비스키 발급 시각. */
            LocalDateTime createdAt
    ) {
        /** 엔티티와 원본 토큰을 발급 응답 DTO로 변환한다. */
        public static CreateResponse of(OpenApiToken token, String rawToken) {
            return new CreateResponse(
                    token.getId(),
                    token.getName(),
                    rawToken,
                    token.getTokenPrefix(),
                    token.getExpiresAt(),
                    token.getCreatedAt()
            );
        }
    }

    /** 서비스키 목록 조회 응답. 보안상 원본 token은 포함하지 않는다. */
    public record Response(
            /** 서비스키 식별자. */
            Long id,
            /** 서비스키 이름. */
            String name,
            /** 목록에서 식별 가능한 토큰 앞부분. */
            String tokenPrefix,
            /** 서비스키 만료 시각. */
            LocalDateTime expiresAt,
            /** 마지막 사용 시각. */
            LocalDateTime lastUsedAt,
            /** 폐기 시각. null이면 폐기되지 않은 상태이다. */
            LocalDateTime revokedAt,
            /** 서비스키 발급 시각. */
            LocalDateTime createdAt,
            /** 현재 시각 기준 사용 가능 여부. */
            boolean active
    ) {
        /** 엔티티를 목록 응답 DTO로 변환한다. */
        public static Response from(OpenApiToken token) {
            return new Response(
                    token.getId(),
                    token.getName(),
                    token.getTokenPrefix(),
                    token.getExpiresAt(),
                    token.getLastUsedAt(),
                    token.getRevokedAt(),
                    token.getCreatedAt(),
                    token.isActive(LocalDateTime.now())
            );
        }
    }
}
