package com.disaster.alert.alertapi.domain.notification.service;

import com.disaster.alert.alertapi.domain.member.model.Member;
import com.disaster.alert.alertapi.domain.notification.dto.FcmTokenDtos;
import com.disaster.alert.alertapi.domain.notification.model.FcmToken;
import com.disaster.alert.alertapi.domain.notification.model.GuestFcmRegion;
import com.disaster.alert.alertapi.domain.notification.repository.FcmTokenRepository;
import com.disaster.alert.alertapi.domain.notification.repository.GuestFcmRegionRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GuestFcmTokenService {

    private static final int MAX_GUEST_REGIONS = 5;

    private final FcmTokenRepository fcmTokenRepository;
    private final GuestFcmRegionRepository guestFcmRegionRepository;
    private final EntityManager entityManager;

    /**
     * 비로그인 FCM 토큰 + 관심지역 등록
     * fcm_token 테이블에 member=null로 저장하고, guest_fcm_region에 지역 코드를 저장한다.
     */
    public void registerGuestToken(FcmTokenDtos.GuestRegisterRequest request) {
        String token = request.token();
        List<String> codes = request.legalDistrictCodes();

        if (codes.size() > MAX_GUEST_REGIONS) {
            throw new IllegalArgumentException("관심지역은 최대 " + MAX_GUEST_REGIONS + "개까지 등록할 수 있습니다.");
        }

        // fcm_token UPSERT (member_id = null)
        fcmTokenRepository.findByToken(token).ifPresentOrElse(
                existing -> {
                    // 이미 회원에 연결된 토큰이면 게스트 등록 스킵
                    if (existing.getMember() != null) return;
                    existing.updateToken(token);
                },
                () -> fcmTokenRepository.save(
                        FcmToken.builder()
                                .member(null)
                                .token(token)
                                .deviceType(request.deviceType())
                                .build()
                )
        );

        // 기존 게스트 지역 모두 교체
        guestFcmRegionRepository.deleteByFcmToken(token);
        for (String code : codes) {
            guestFcmRegionRepository.save(
                    GuestFcmRegion.builder()
                            .fcmToken(token)
                            .legalDistrictCode(code)
                            .build()
            );
        }
    }

    /**
     * 로그인 시 게스트 FCM 토큰을 회원과 연결하고 게스트 관심지역을 정리한다.
     */
    public void linkGuestTokenToMember(Long memberId, String token) {
        fcmTokenRepository.findByToken(token).ifPresent(fcmToken -> {
            if (fcmToken.getMember() != null) return;
            Member memberRef = entityManager.getReference(Member.class, memberId);
            fcmToken.linkMember(memberRef);
            guestFcmRegionRepository.deleteByFcmToken(token);
            log.info("게스트 FCM 토큰 회원 연결 완료 - memberId: {}", memberId);
        });
    }

    /**
     * 게스트 FCM 토큰 삭제 (브라우저 알림 해제 시)
     */
    public void deleteGuestToken(String token) {
        guestFcmRegionRepository.deleteByFcmToken(token);
        fcmTokenRepository.findByToken(token).ifPresent(fcmToken -> {
            if (fcmToken.getMember() == null) {
                fcmTokenRepository.delete(fcmToken);
            }
        });
    }
}
