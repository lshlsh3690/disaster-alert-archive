package com.disaster.alert.alertapi.domain.useralert.service;

import com.disaster.alert.alertapi.domain.common.exception.CustomException;
import com.disaster.alert.alertapi.domain.common.exception.ErrorCode;
import com.disaster.alert.alertapi.domain.disasteralert.dto.UserAlertDtos;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.disaster.alert.alertapi.domain.member.model.MemberRole;
import com.disaster.alert.alertapi.domain.legaldistrict.model.LegalDistrict;
import com.disaster.alert.alertapi.domain.useralert.model.UserDisasterAlert;
import com.disaster.alert.alertapi.domain.useralert.model.UserDisasterAlertRegion;
import com.disaster.alert.alertapi.domain.useralert.repository.UserDisasterAlertRegionRepository;
import com.disaster.alert.alertapi.domain.useralert.repository.UserDisasterAlertRepository;
import com.disaster.alert.alertapi.domain.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityManager;
import com.disaster.alert.alertapi.global.service.LegalDistrictCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserDisasterAlertService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserDisasterAlertRepository userDisasterAlertRepository;
    private final UserDisasterAlertRegionRepository userDisasterAlertRegionRepository;
    private final EntityManager entityManager;
    private final LegalDistrictCache legalDistrictCache;
    private final MemberService memberService;

    /**
     * 생성
     */
    public UserAlertDtos.Response create(Long memberId, UserAlertDtos.CreateRequest req) {
        // 기본 엔티티 생성/저장(우선 저장해서 PK 발급)
        UserDisasterAlert alert = UserDisasterAlert.builder()
                .createdById(memberId)
                .title(req.getTitle().trim())
                .message(req.getMessage().trim())
                .disasterType(trimToNull(req.getDisasterType()))
                .disasterLevel(req.getDisasterLevel() != null ? DisasterLevel.valueOf(req.getDisasterLevel()) : null)
                .occurredAt(req.getOccurredAt())
                .build();

        userDisasterAlertRepository.save(alert);

        req.getRegionCodes().stream()
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .distinct()
                .forEach(code -> {
                    if (!legalDistrictCache.existsCode(code)) {
                        throw new CustomException(ErrorCode.INVALID_REQUEST, "존재하지 않는 법정동 코드: " + code);
                    }
                    LegalDistrict ref = entityManager.getReference(LegalDistrict.class, code);
                    alert.getRegions().add(new UserDisasterAlertRegion(alert, ref));
                });

        log.info("UserDisasterAlert created: id={}, memberId={} title={}", alert.getId(), memberId, alert.getTitle());

        return UserAlertDtos.Response.from(alert);
    }

    /**
     * 단건 조회
     */
    @Transactional(readOnly = true)
    public UserAlertDtos.Response get(Long id) {
        UserDisasterAlert e = userDisasterAlertRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_ALERT_NOT_FOUND, "제보가 존재하지 않습니다."));
        // LAZY 초기화 트리거
        e.getRegions().size();
        String nickname = null;
        try {
            nickname = memberService.getMemberInfo(e.getCreatedById()).getNickname();
        } catch (Exception ignored) {}
        return UserAlertDtos.Response.fromWithNickname(e, nickname);
    }

    /**
     * 수정
     * - 컨트롤러에서 @PreAuthorize로 권한 체크 완료
     */
    public UserAlertDtos.Response update(Long id, UserAlertDtos.UpdateRequest req, Long principalId, MemberRole role) {
        // 엔티티 조회
        UserDisasterAlert e = userDisasterAlertRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_ALERT_NOT_FOUND, "제보가 존재하지 않습니다."));

        // 일반 사용자는 본인 글만 수정 가능 (이중 체크)
        if (role != MemberRole.ADMIN && !Objects.equals(e.getCreatedById(), principalId)) {
            throw new CustomException(ErrorCode.FORBIDDEN, "작성자 본인만 수정할 수 있습니다.");
        }

        // 비즈니스 로직: 필드 업데이트
        if (req.getTitle() != null && !req.getTitle().isBlank()) {
            e.setTitle(req.getTitle().trim());
        }
        if (req.getMessage() != null && !req.getMessage().isBlank()) {
            e.setMessage(req.getMessage().trim());
        }
        if (req.getDisasterType() != null) {
            e.setDisasterType(trimToNull(req.getDisasterType()));
        }
        if (req.getDisasterLevel() != null) {
            e.setDisasterLevel(DisasterLevel.valueOf(req.getDisasterLevel()));
        }
        if (req.getOccurredAt() != null) {
            e.setOccurredAt(req.getOccurredAt());
        }

        // 지역 정보 업데이트
        if (req.getRegionCodes() != null) {
            // 기존 지역 정보 삭제
            userDisasterAlertRegionRepository.deleteByIdUserDisasterAlertId(e.getId());
            e.getRegions().clear();
            
            // 새로운 지역 정보 추가
            req.getRegionCodes().stream()
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .forEach(code -> {
                        if (!legalDistrictCache.existsCode(code)) {
                            throw new CustomException(ErrorCode.INVALID_REQUEST, "존재하지 않는 법정동 코드: " + code);
                        }
                        LegalDistrict ref = entityManager.getReference(LegalDistrict.class, code);
                        e.getRegions().add(new UserDisasterAlertRegion(e, ref));
                    });
        }

        // LAZY 초기화 트리거
        e.getRegions().size();
        return UserAlertDtos.Response.from(e);
    }

    /**
     * 목록 조회 (mine=true 처리용 ownerId 존재 시 작성자 한정)
     */
    @Transactional(readOnly = true)
    public Page<UserAlertDtos.Response> list(Pageable pageable, Long ownerId) {
        Page<UserDisasterAlert> page = (ownerId != null)
                ? userDisasterAlertRepository.findByCreatedById(pageable, ownerId)
                : userDisasterAlertRepository.findAllWithRegions(pageable);

        return page.map(UserAlertDtos.Response::from);
    }

    /**
     * 삭제
     * - 컨트롤러에서 @PreAuthorize로 권한 체크 완료
     */
    public void delete(Long id, Long memberId, MemberRole role) {
        // 엔티티 조회
        UserDisasterAlert e = userDisasterAlertRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_ALERT_NOT_FOUND, "제보가 존재하지 않습니다."));

        // 일반 사용자는 본인 글만 삭제 가능 (이중 체크)
        if (role != MemberRole.ADMIN && !Objects.equals(e.getCreatedById(), memberId)) {
            throw new CustomException(ErrorCode.FORBIDDEN, "작성자 본인만 삭제할 수 있습니다.");
        }

        // 소프트 삭제
        e.setDeleted(true);
    }

    @Transactional(readOnly = true)
    public long countAllActive() {
        return userDisasterAlertRepository.countAllActive();
    }

    @Transactional(readOnly = true)
    public long countTodayActive() {
        return userDisasterAlertRepository.countTodayActive(LocalDate.now(KST).atStartOfDay());
    }

    /* -------------------- 헬퍼 -------------------- */

    /**
     * 문자열을 trim하고, 빈 문자열이면 null 반환
     */
    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /* -------------------- 권한 체크 (for @PreAuthorize) -------------------- */

    /**
     * 해당 게시물의 작성자인지 확인
     * @PreAuthorize에서 사용
     */
    public boolean isOwner(Long alertId, Long memberId) {
        if (alertId == null || memberId == null) return false;
        return userDisasterAlertRepository.findById(alertId)
                .map(alert -> Objects.equals(alert.getCreatedById(), memberId))
                .orElse(false);
    }

    /**
     * 해당 게시물의 작성자이거나 관리자인지 확인
     * @PreAuthorize에서 사용
     */
    public boolean isOwnerOrAdmin(Long alertId, Long memberId, MemberRole role) {
        if (role == MemberRole.ADMIN) return true;
        return isOwner(alertId, memberId);
    }


}
