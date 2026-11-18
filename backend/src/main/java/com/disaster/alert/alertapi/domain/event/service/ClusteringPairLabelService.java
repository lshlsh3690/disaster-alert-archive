package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.common.exception.CustomException;
import com.disaster.alert.alertapi.domain.event.dto.ClusteringPairLabelRequest;
import com.disaster.alert.alertapi.domain.event.dto.ClusteringPairLabelResponse;
import com.disaster.alert.alertapi.domain.event.repository.ClusteringDailyScorePairRepository;
import com.disaster.alert.alertapi.domain.event.repository.ClusteringPairLabelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 불일치 쌍 사람 라벨 저장 service. 관리자가 대시보드에서 쌍마다 "같은 사건 / 다른 사건 / 모르겠음"을 매기면
 * 쌍당 한 줄로 저장하고, 다시 매기면 덮어쓴다(설계: specs/009 research R1·R4).
 *
 * <p>Red 단계 뼈대 - 아무것도 저장하지 않고 틀린 기본값(라벨·기준 시점·저장 시각 null)만 돌려준다. 구현은 Green 에서.
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
     * @throws CustomException 위 검증에 걸리면
     */
    public ClusteringPairLabelResponse save(Long memberId, ClusteringPairLabelRequest request) {
        return new ClusteringPairLabelResponse(
                Math.min(request.alertA(), request.alertB()), Math.max(request.alertA(), request.alertB()),
                null, null, null);
    }
}
