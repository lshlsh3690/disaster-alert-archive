package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.event.dto.EvalLabelResponse;
import com.disaster.alert.alertapi.domain.event.dto.EvalLabelUpdateRequest;
import com.disaster.alert.alertapi.domain.event.dto.EvalLabelsResponse;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.repository.EvaluationLabelHistoryRepository;
import com.disaster.alert.alertapi.domain.event.repository.EvaluationLabelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 평가 셋 사람 라벨을 관리자 화면에서 조회·수정하는 service. 라벨이 평가의 정답이라, 바꿀 때는 사유를 받아
 * 이력(evaluation_label_history)에 남기고 같은 라벨로 저장하면 아무것도 바꾸지 않는다.
 *
 * <p>Red 단계 뼈대 - 빈 목록을 돌려주고 수정은 아무것도 저장하지 않는다.
 */
// Red 단계 뼈대 — 구현은 Green 에서
@Service
@RequiredArgsConstructor
@Transactional
public class EvaluationLabelService {

    private final EvaluationLabelRepository labelRepository;
    private final EvaluationLabelHistoryRepository historyRepository;

    /** 라벨 목록. dataset·label 은 주면 그 값으로만 거르고 생략하면 거르지 않는다. */
    @Transactional(readOnly = true)
    public EvalLabelsResponse list(String dataset, Label label, int page, int size) {
        // Red 단계 뼈대 — 구현은 Green 에서
        return new EvalLabelsResponse(page, size, 0, 0, new EvalLabelsResponse.LabelCounts(0, 0, 0), List.of());
    }

    /**
     * 라벨을 고친다. 없는 id 면 404({@code EVAL_LABEL_NOT_FOUND}).
     *
     * @param memberId 고치는 관리자의 회원 id
     */
    public EvalLabelResponse update(Long memberId, long id, EvalLabelUpdateRequest request) {
        // Red 단계 뼈대 — 구현은 Green 에서
        return new EvalLabelResponse(id, null, null, null);
    }
}
