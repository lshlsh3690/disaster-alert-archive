package com.disaster.alert.alertapi.domain.openapi.model;

import com.disaster.alert.alertapi.domain.member.model.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * OpenAPI 서비스키 엔티티.
 *
 * <p>회원별로 발급된 외부 API 접근 키의 상태를 저장한다.
 * 보안상 원본 토큰은 저장하지 않고 SHA-256 해시만 저장하며, 만료와 폐기를 통해 운영 중 키를 통제한다.
 */
@Entity
@Table(name = "open_api_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class OpenApiToken {
    /** OpenAPI 서비스키 식별자. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "open_api_token_id")
    private Long id;

    /** 서비스키를 발급받은 회원. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /** 사용자가 관리 목적으로 지정하는 서비스키 이름. */
    @Column(nullable = false, length = 100)
    private String name;

    /** 원본 서비스키를 SHA-256으로 변환한 값. 인증 시 이 값으로 비교한다. */
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** 사용자가 목록에서 키를 식별할 수 있도록 저장하는 원본 토큰의 앞부분. */
    @Column(nullable = false, length = 20)
    private String tokenPrefix;

    /** 서비스키 만료 시각. 이 시각 이후에는 인증에 실패한다. */
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /** 마지막으로 OpenAPI 요청에 성공적으로 사용된 시각. */
    private LocalDateTime lastUsedAt;

    /** 서비스키가 폐기된 시각. null이면 폐기되지 않은 상태이다. */
    private LocalDateTime revokedAt;

    /** 서비스키 발급 시각. */
    @CreatedDate
    private LocalDateTime createdAt;

    /** 서비스키 엔티티를 생성한다. 원본 토큰은 받지 않고 해시와 prefix만 저장한다. */
    public static OpenApiToken create(Member member, String name, String tokenHash, String tokenPrefix, LocalDateTime expiresAt) {
        return OpenApiToken.builder()
                .member(member)
                .name(name)
                .tokenHash(tokenHash)
                .tokenPrefix(tokenPrefix)
                .expiresAt(expiresAt)
                .build();
    }

    /** 현재 시각 기준으로 폐기되지 않았고 만료되지 않았는지 판단한다. */
    public boolean isActive(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    /** 해당 서비스키가 요청 회원의 소유인지 확인한다. */
    public boolean isOwnedBy(Long memberId) {
        return member.getId().equals(memberId);
    }

    /** 서비스키를 폐기 상태로 변경한다. */
    public void revoke() {
        this.revokedAt = LocalDateTime.now();
    }

    /** 인증에 성공한 시각을 기록한다. */
    public void markUsed() {
        this.lastUsedAt = LocalDateTime.now();
    }
}
