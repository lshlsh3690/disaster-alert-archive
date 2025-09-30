package com.disaster.alert.alertapi.domain.useralert.service;

import com.disaster.alert.alertapi.domain.disasteralert.dto.UserAlertDtos;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.disaster.alert.alertapi.domain.useralert.model.UserDisasterAlert;
import com.disaster.alert.alertapi.domain.useralert.model.UserDisasterAlertRegion;
import com.disaster.alert.alertapi.domain.useralert.repository.UserDisasterAlertRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static java.rmi.server.LogStream.parseLevel;

@Service
@RequiredArgsConstructor
@Transactional
public class UserDisasterAlertService {

    private final UserDisasterAlertRepository userDisasterAlertRepository;

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
                .disasterLevel(DisasterLevel.valueOf(req.getDisasterLevel()))
                .occurredAt(req.getOccurredAt())
                .build();

        userDisasterAlertRepository.save(alert);

        req.getRegionCodes().stream()
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .distinct()
                .forEach(code -> alert.getRegions().add(new UserDisasterAlertRegion(alert, code)));

        return UserAlertDtos.Response.from(alert);
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
     * 수정
     */
    public UserAlertDtos.Response update(Long id, Long memberId, UserAlertDtos.UpdateRequest req, boolean admin) {
        UserDisasterAlert e = userDisasterAlertRepository.findByIdWithRegions(id)
                .orElseThrow(() -> new EntityNotFoundException("User alert not found: " + id));
        enforceOwnership(e, memberId, admin);

        if (req.getTitle() != null) e.setTitle(req.getTitle().trim());
        if (req.getMessage() != null) e.setMessage(req.getMessage().trim());
        if (req.getDisasterType() != null) e.setDisasterType(trimToNull(req.getDisasterType()));
        if (req.getEmergencyLevel() != null) e.setEmergencyLevel(parseLevel(req.getDisasterLevel()));
        if (req.getOccurredAt() != null) e.setOccurredAt(req.getOccurredAt());

        if (req.getDistrictCodes() != null) {
            e.getRegions().clear();
            for (String code : req.getDistrictCodes()) {
                e.getRegions().add(new UserDisasterAlertRegion(e, code));
            }
        }

        return UserAlertDtos.Response.from(e);
    }

    /**
     * 삭제
     */
    public void delete(Long id, Long memberId, boolean admin) {
        UserDisasterAlert e = userDisasterAlertRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User alert not found: " + id));
        enforceOwnership(e, memberId, admin);
        userDisasterAlertRepository.delete(e);
    }

    /* -------------------- 헬퍼 -------------------- */

    private void enforceOwnership(UserDisasterAlert e, Long memberId, boolean admin) {
        if (admin) return;
        if (!Objects.equals(e.getCreatedById(), memberId)) {
            throw new AccessDeniedException("수정/삭제 권한이 없습니다.");
        }
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
