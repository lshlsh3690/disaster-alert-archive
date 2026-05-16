// domain/notification/service/FcmTokenService.java
package com.disaster.alert.alertapi.domain.notification.service;

import com.disaster.alert.alertapi.domain.member.model.Member;
import com.disaster.alert.alertapi.domain.notification.dto.FcmTokenDtos;
import com.disaster.alert.alertapi.domain.notification.model.FcmToken;
import com.disaster.alert.alertapi.domain.notification.repository.FcmTokenRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class FcmTokenService {

    private final FcmTokenRepository fcmTokenRepository;
    private final EntityManager entityManager;

    // 토큰 등록 or 갱신 (UPSERT)
    public void registerToken(Long memberId, FcmTokenDtos.RegisterRequest request) {
        fcmTokenRepository
                .findByMemberIdAndDeviceType(memberId, request.deviceType())
                .ifPresentOrElse(
                        // 기존 토큰 갱신
                        existing -> existing.updateToken(request.token()),
                        // 신규 토큰 등록
                        () -> {
                            Member memberRef = entityManager.getReference(Member.class, memberId);
                            fcmTokenRepository.save(
                                    FcmToken.builder()
                                            .member(memberRef)
                                            .token(request.token())
                                            .deviceType(request.deviceType())
                                            .build()
                            );
                        }
                );
    }

    // 토큰 삭제 (로그아웃 시)
    public void deleteToken(String token) {
        fcmTokenRepository.deleteByToken(token);
    }
}