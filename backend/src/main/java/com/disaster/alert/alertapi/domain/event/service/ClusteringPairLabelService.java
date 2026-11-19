package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.common.exception.CustomException;
import com.disaster.alert.alertapi.domain.common.exception.ErrorCode;
import com.disaster.alert.alertapi.domain.event.dto.ClusteringPairLabelRequest;
import com.disaster.alert.alertapi.domain.event.dto.ClusteringPairLabelResponse;
import com.disaster.alert.alertapi.domain.event.evaluation.ClusteringCriteria;
import com.disaster.alert.alertapi.domain.event.model.ClusteringPairLabel;
import com.disaster.alert.alertapi.domain.event.repository.ClusteringDailyScorePairRepository;
import com.disaster.alert.alertapi.domain.event.repository.ClusteringPairLabelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 불일치 쌍 사람 라벨 저장 service. 관리자가 대시보드에서 쌍마다 "같은 사건 / 다른 사건 / 모르겠음"을 매기면
 * 쌍당 한 줄로 저장하고, 다시 매기면 덮어쓴다(설계: specs/009 research R1·R4).
 *
 * <p>쓰기는 이 클래스에만 모은다 — 조회용 {@code ClusteringDashboardService} 는 클래스 단위 readOnly 라 섞지 않는다(R9).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ClusteringPairLabelService {

    private final ClusteringPairLabelRepository labelRepository;
    private final ClusteringDailyScorePairRepository scorePairRepository;

    /**
     * 라벨을 저장한다. 두 id 는 작은 쪽을 a 로 맞추고, 불일치 쌍으로 기록된 적 없는 쌍이면 404
     * ({@code CLUSTERING_PAIR_NOT_FOUND}), 두 id 가 같으면 400.
     *
     * @param memberId 라벨을 매기는 관리자의 회원 id
     * @return 저장된 쌍(작은 id 가 a)·라벨·기준 시점·저장 시각
     * @throws CustomException 위 검증에 걸리면
     */
    public ClusteringPairLabelResponse save(Long memberId, ClusteringPairLabelRequest request) {
        // 1) 작은 id 를 a 로 맞춘다 — 같은 쌍이 (1,2)·(2,1) 두 줄로 들어가지 않게(DB 도 alert_a < alert_b 를 강제한다)
        long alertA = Math.min(request.alertA(), request.alertB());
        long alertB = Math.max(request.alertA(), request.alertB());
        // 2) 자기 자신과의 쌍은 쌍이 아니다 — DB 체크 제약에 걸려 500 이 되기 전에 400 으로 돌려준다
        if (alertA == alertB) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "서로 다른 두 알림이어야 합니다.");
        }
        // 3) 불일치로 기록된 적 있는 쌍만 받는다 — 아무 쌍이나 받으면 어려운 쌍 성적과 평가 정답의 의미가 흐려진다(R10)
        if (!scorePairRepository.existsByAlertAAndAlertBAndDisagreementIsNotNull(alertA, alertB)) {
            throw new CustomException(ErrorCode.CLUSTERING_PAIR_NOT_FOUND);
        }
        // 4) 기준 시점은 요청이 아니라 서버 상수에서 붙인다 — 사람이 잘못 입력할 수 없게(R7)
        LocalDateTime now = LocalDateTime.now();
        Optional<ClusteringPairLabel> existing = labelRepository.findByAlertAAndAlertB(alertA, alertB);
        // 5) 줄이 없으면 새로 넣는다. 1 이면 방금 우리가 처음 저장한 것이라 입력값이 곧 저장된 값이다
        if (existing.isEmpty() && labelRepository.insertIfAbsent(alertA, alertB, request.label().name(),
                ClusteringCriteria.VERSION_DATE, memberId, now) == 1) {
            return new ClusteringPairLabelResponse(
                    alertA, alertB, request.label(), ClusteringCriteria.VERSION_DATE, now);
        }
        // 6) 줄이 이미 있었거나(재저장), 찾은 직후 다른 요청이 먼저 넣었다(0)면 그 줄을 고친다 — 나중 저장이 남는다(R4)
        ClusteringPairLabel label = existing
                .or(() -> labelRepository.findByAlertAAndAlertB(alertA, alertB))
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));
        // 수정 시각은 기존 줄을 확보한 뒤 다시 잰다 — 위 now 는 다른 요청의 첫 저장(ON CONFLICT 대기)보다 앞설 수 있어, 그대로 쓰면
        // 나중 저장인데 updated_at 이 먼저 저장된 줄보다 이른 시각으로 되돌아간다(CodeRabbit PR #342)
        label.relabel(request.label(), ClusteringCriteria.VERSION_DATE, memberId, LocalDateTime.now());
        return new ClusteringPairLabelResponse(
                alertA, alertB, label.getLabel(), label.getCriteriaDate(), label.getUpdatedAt());
    }
}
