package com.disaster.alert.alertapi.domain.openapi.repository;

import com.disaster.alert.alertapi.domain.openapi.model.OpenApiToken;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * OpenAPI 서비스키 저장소.
 *
 * <p>서비스키 원문은 저장하지 않으므로 인증 조회는 tokenHash 기준으로 수행한다.
 */
public interface OpenApiTokenRepository extends JpaRepository<OpenApiToken, Long> {
    /** 요청 서비스키의 SHA-256 해시로 발급 이력을 조회한다. */
    Optional<OpenApiToken> findByTokenHash(String tokenHash);

    /** 회원이 발급받은 서비스키 목록을 최신 발급순으로 페이징 조회한다. */
    Page<OpenApiToken> findAllByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);
}
