package com.disaster.alert.alertapi.domain.openapi.service;

import com.disaster.alert.alertapi.domain.common.exception.CustomException;
import com.disaster.alert.alertapi.domain.common.exception.ErrorCode;
import com.disaster.alert.alertapi.domain.member.model.Member;
import com.disaster.alert.alertapi.domain.member.repository.MemberRepository;
import com.disaster.alert.alertapi.domain.openapi.dto.OpenApiTokenDtos;
import com.disaster.alert.alertapi.domain.openapi.model.OpenApiToken;
import com.disaster.alert.alertapi.domain.openapi.repository.OpenApiTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
/**
 * OpenAPI 서비스키 발급과 검증을 담당하는 서비스.
 *
 * <p>서비스키 원문은 발급 응답에서만 노출하고, 저장소에는 SHA-256 해시만 저장한다.
 * 만료와 폐기는 DB 필드로 관리해 운영자가 발급 이력과 상태를 추적할 수 있게 했다.
 */
public class OpenApiTokenService {
    /** OpenAPI 서비스키임을 구분하기 위한 접두사. */
    private static final String TOKEN_PREFIX = "daa_";

    /** 요청에서 만료 기간을 생략했을 때 사용하는 기본 유효 기간. */
    private static final int DEFAULT_EXPIRES_IN_DAYS = 30;

    /** 예측 불가능한 서비스키 생성을 위한 보안 난수 생성기. */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 서비스키 상태를 저장하고 조회하는 JPA repository. */
    private final OpenApiTokenRepository openApiTokenRepository;

    /** 서비스키를 발급받는 회원을 검증하기 위한 회원 repository. */
    private final MemberRepository memberRepository;

    /**
     * 로그인한 회원에게 OpenAPI 서비스키를 발급한다.
     *
     * <p>원본 토큰은 응답으로만 반환하고, DB에는 해시와 일부 prefix만 저장한다.
     */
    @Transactional
    public OpenApiTokenDtos.CreateResponse create(Long memberId, OpenApiTokenDtos.CreateRequest request) {
        Member member = memberRepository.findByIdAndIsDeletedFalse(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        String rawToken = generateRawToken();
        String tokenHash = hash(rawToken);
        int expiresInDays = request.expiresInDays() == null ? DEFAULT_EXPIRES_IN_DAYS : request.expiresInDays();

        OpenApiToken openApiToken = OpenApiToken.create(
                member,
                request.name().trim(),
                tokenHash,
                rawToken.substring(0, Math.min(12, rawToken.length())),
                LocalDateTime.now().plusDays(expiresInDays)
        );

        return OpenApiTokenDtos.CreateResponse.of(openApiTokenRepository.save(openApiToken), rawToken);
    }

    /** 회원이 발급받은 서비스키 목록을 최신순으로 조회한다. */
    @Transactional(readOnly = true)
    public List<OpenApiTokenDtos.Response> findMine(Long memberId) {
        return openApiTokenRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId)
                .stream()
                .map(OpenApiTokenDtos.Response::from)
                .toList();
    }

    /** 회원 소유의 서비스키를 폐기한다. 다른 회원의 키는 폐기할 수 없다. */
    @Transactional
    public void revoke(Long memberId, Long tokenId) {
        OpenApiToken token = openApiTokenRepository.findById(tokenId)
                .orElseThrow(() -> new CustomException(ErrorCode.OPEN_API_TOKEN_NOT_FOUND));

        if (!token.isOwnedBy(memberId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        token.revoke();
    }

    /**
     * 외부 API 요청에 포함된 서비스키를 검증하고 마지막 사용 시각을 갱신한다.
     *
     * <p>필터에서 호출되며, 원본 토큰을 해시한 뒤 저장된 해시와 비교한다.
     */
    @Transactional
    public boolean validateAndMarkUsed(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }

        return openApiTokenRepository.findByTokenHash(hash(rawToken))
                .filter(token -> token.isActive(LocalDateTime.now()))
                .map(token -> {
                    token.markUsed();
                    return true;
                })
                .orElse(false);
    }

    /** URL-safe Base64 문자열을 사용해 외부 전달 가능한 서비스키 원문을 생성한다. */
    private String generateRawToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /** 서비스키 원문을 저장하지 않기 위해 SHA-256 해시 문자열로 변환한다. */
    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
}
