// domain/notification/service/FcmTokenService.java
package com.disaster.alert.alertapi.domain.notification.service;

import com.disaster.alert.alertapi.domain.member.model.Member;
import com.disaster.alert.alertapi.domain.notification.dto.FcmTokenDtos;
import com.disaster.alert.alertapi.domain.notification.model.FcmToken;
import com.disaster.alert.alertapi.domain.notification.repository.FcmTokenRepository;
import com.disaster.alert.alertapi.domain.notification.repository.GuestFcmRegionRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class FcmTokenService {

    private final FcmTokenRepository fcmTokenRepository;
    private final GuestFcmRegionRepository guestFcmRegionRepository;
    private final EntityManager entityManager;

    // 토큰 등록 or 갱신 (UPSERT)
    public void registerToken(Long memberId, FcmTokenDtos.RegisterRequest request) {
        String token = request.token();

        // 이 회원 + 기기의 기존 토큰 행 (있으면 토큰만 갱신할 대상)
        FcmToken deviceRow = fcmTokenRepository
                .findByMemberIdAndDeviceType(memberId, request.deviceType())
                .orElse(null);

        // 동일 토큰이 다른 행(게스트로 먼저 등록됐거나 다른 기기 행)에 있으면
        // 전역 UNIQUE(token) 충돌을 피하기 위해 그 행을 먼저 삭제·flush 한다.
        // (flush 하지 않으면 아래 update/insert 가 먼저 실행돼 순간적으로 토큰이 중복된다.)
        // 게스트 행이었다면 guest_fcm_region 매핑도 함께 지운다 — 안 지우면 그 토큰이
        // 회원용으로 바뀐 뒤에도 게스트 발송 경로(findAllByLegalDistrictCodeIn)에 잡혀 중복 푸시가 된다.
        fcmTokenRepository.findByToken(token)
                .filter(row -> deviceRow == null || !row.getId().equals(deviceRow.getId()))
                .ifPresent(row -> {
                    guestFcmRegionRepository.deleteByFcmToken(token);
                    fcmTokenRepository.delete(row);
                    fcmTokenRepository.flush();
                });

        if (deviceRow != null) {
            deviceRow.updateToken(token);
        } else {
            Member memberRef = entityManager.getReference(Member.class, memberId);
            fcmTokenRepository.save(
                    FcmToken.builder()
                            .member(memberRef)
                            .token(token)
                            .deviceType(request.deviceType())
                            .build()
            );
        }
    }

    // 토큰 삭제 (로그아웃 시)
    public void deleteToken(String token) {
        fcmTokenRepository.deleteByToken(token);
    }
}