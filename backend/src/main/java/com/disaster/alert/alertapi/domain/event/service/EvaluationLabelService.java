package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.common.exception.CustomException;
import com.disaster.alert.alertapi.domain.common.exception.ErrorCode;
import com.disaster.alert.alertapi.domain.disasteralert.dto.AlertSummaryDto;
import com.disaster.alert.alertapi.domain.disasteralert.service.DisasterAlertService;
import com.disaster.alert.alertapi.domain.event.dto.ClusteringDashboardDisagreementsResponse.AlertSummary;
import com.disaster.alert.alertapi.domain.event.dto.EvalLabelResponse;
import com.disaster.alert.alertapi.domain.event.dto.EvalLabelUpdateRequest;
import com.disaster.alert.alertapi.domain.event.dto.EvalLabelsResponse;
import com.disaster.alert.alertapi.domain.event.evaluation.ClusteringCriteria;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.model.EvaluationLabel;
import com.disaster.alert.alertapi.domain.event.model.EvaluationLabelHistory;
import com.disaster.alert.alertapi.domain.event.repository.EvaluationLabelHistoryRepository;
import com.disaster.alert.alertapi.domain.event.repository.EvaluationLabelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 평가 셋 사람 라벨을 관리자 화면에서 조회·수정하는 service. 라벨이 평가의 정답이라, 바꿀 때는 사유를 받아
 * 이력(evaluation_pair_label_history)에 남기고 같은 라벨로 저장하면 아무것도 바꾸지 않는다.
 *
 * <p>알림 원문은 다른 맥락(disasteralert)의 저장소가 아니라 {@link DisasterAlertService} 를 거쳐 읽는다(헌법 VII).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class EvaluationLabelService {

    private static final int MAX_PAGE_SIZE = 100;
    // 문항 번호 순, 같으면 id 순 — 화면에서 행이 매번 같은 자리에 나오게
    private static final Sort ORDER = Sort.by(Sort.Order.asc("q"), Sort.Order.asc("id"));

    private final EvaluationLabelRepository labelRepository;
    private final EvaluationLabelHistoryRepository historyRepository;
    private final DisasterAlertService disasterAlertService;

    /**
     * 라벨 목록. dataset·label 은 주면 그 값으로만 거르고 생략하면 거르지 않는다.
     * labelCounts 는 label 필터와 상관없이 그 셋(셋을 안 주면 전체) 기준이다.
     *
     * @throws CustomException page·size 범위를 벗어나면 {@code INVALID_REQUEST}
     */
    @Transactional(readOnly = true)
    public EvalLabelsResponse list(String dataset, Label label, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "page 는 0 이상, size 는 1~" + MAX_PAGE_SIZE);
        }
        // 1) 필터에 맞는 한 페이지
        Page<EvaluationLabel> rows = findPage(dataset, label, PageRequest.of(page, size, ORDER));
        // 2) 두 알림 원문은 한 번의 조회로 모아 읽는다(쌍마다 조회하지 않게)
        Set<Long> alertIds = new HashSet<>();
        rows.forEach(r -> {
            alertIds.add(r.getAlertAId());
            alertIds.add(r.getAlertBId());
        });
        Map<Long, AlertSummaryDto> summaries = disasterAlertService.findAlertSummaries(alertIds).stream()
                .collect(Collectors.toMap(AlertSummaryDto::id, Function.identity()));
        List<EvalLabelsResponse.Pair> pairs = rows.stream().map(r -> toPair(r, summaries)).toList();
        return new EvalLabelsResponse(page, size, rows.getTotalElements(), rows.getTotalPages(), countLabels(dataset), pairs);
    }

    /**
     * 라벨을 고친다. 라벨이 바뀌면 라벨 행(라벨·기준 시점·수정자·시각)을 고치고 이력을 한 줄 남긴다.
     * 같은 라벨이면 아무것도 바꾸지 않고 현재 값을 돌려준다.
     *
     * @param memberId 고치는 관리자의 회원 id
     * @throws CustomException 없는 id 면 {@code EVAL_LABEL_NOT_FOUND}
     */
    public EvalLabelResponse update(Long memberId, long id, EvalLabelUpdateRequest request) {
        EvaluationLabel label = labelRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.EVAL_LABEL_NOT_FOUND));
        // 1) 판단이 안 바뀌었으면 이력·수정자를 남기지 않는다 — 진짜 수정 이력이 "바뀐 적 없는 기록"에 묻히지 않게
        if (label.getLabel() == request.label()) {
            return toResponse(label);
        }
        // 2) 기준 시점은 요청이 아니라 서버 상수 — 지금 기준으로 다시 본 라벨임을 남긴다
        Label old = label.getLabel();
        label.relabel(request.label(), ClusteringCriteria.VERSION_DATE, memberId);
        historyRepository.save(EvaluationLabelHistory.of(
                label.getId(), old, request.label(), request.reason().trim(), memberId));
        // 3) updated_at 은 UPDATE 가 나갈 때 Auditing 이 채운다 — 응답에 새 시각이 담기도록 여기서 내보낸다
        labelRepository.saveAndFlush(label);
        return toResponse(label);
    }

    private Page<EvaluationLabel> findPage(String dataset, Label label, Pageable pageable) {
        if (dataset == null) {
            return label == null ? labelRepository.findAll(pageable) : labelRepository.findByLabel(label, pageable);
        }
        return label == null
                ? labelRepository.findByDataset(dataset, pageable)
                : labelRepository.findByDatasetAndLabel(dataset, label, pageable);
    }

    private EvalLabelsResponse.LabelCounts countLabels(String dataset) {
        Function<Label, Long> count = dataset == null
                ? labelRepository::countByLabel
                : l -> labelRepository.countByDatasetAndLabel(dataset, l);
        return new EvalLabelsResponse.LabelCounts(count.apply(Label.SAME), count.apply(Label.DIFF), count.apply(Label.UNSURE));
    }

    private static EvalLabelsResponse.Pair toPair(EvaluationLabel r, Map<Long, AlertSummaryDto> summaries) {
        return new EvalLabelsResponse.Pair(r.getId(), r.getQ(), r.getRelation(), r.getLabel(), r.getCriteriaDate(),
                r.getUpdatedAt(), summaryOf(r.getAlertAId(), summaries), summaryOf(r.getAlertBId(), summaries));
    }

    // 알림을 못 찾으면 id 만 채운다(화면은 "원문 없음") — 불일치 쌍 목록과 같은 규칙
    private static AlertSummary summaryOf(long alertId, Map<Long, AlertSummaryDto> summaries) {
        AlertSummaryDto s = summaries.get(alertId);
        return s == null
                ? new AlertSummary(alertId, null, null, null)
                : new AlertSummary(alertId, s.sentAt(), s.type(), s.message());
    }

    private static EvalLabelResponse toResponse(EvaluationLabel label) {
        return new EvalLabelResponse(label.getId(), label.getLabel(), label.getCriteriaDate(), label.getUpdatedAt());
    }
}
