package com.disaster.alert.alertapi.domain.event.evaluation;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 운영 사본 DB 의 기존 이벤트 배정(event_alert_mapping)을 평가 라벨과 비교해 쌍 단위 보고서를 만든다.
 * 평가 도구이며 운영 요청 경로에서 호출되지 않는다.
 *
 * <p>FN 의 원인(후보 검색 실패 vs 판정 실패)을 가르려고 b 시점의 후보 검색을 재현하는데, 과거 이벤트 상태는
 * 저장돼 있지 않으므로 <b>현재 이벤트 상태에서 시점을 근사</b>한다(b 이전 알림만 본다). 이후 병합·분리·수동 수정이
 * 있었다면 당시 후보와 다를 수 있다.
 */
@Service
@RequiredArgsConstructor
public class EventEvaluationService {

    private final EvaluationLabelRepository labelRepository;
    private final EvaluationAssignmentReader reader;

    // EventClusteringService 와 같은 키·기본값 — 후보 윈도우가 어긋나면 RETRIEVAL_MISS 판정이 실제와 달라진다.
    @Value("${clustering.candidate-time-window-hours:168}")
    private int candidateTimeWindowHours;

    @Transactional(readOnly = true)
    public PairwiseReport evaluate(String dataset) {
        List<EvaluatedPair> pairs = new ArrayList<>();
        for (EvaluationLabel label : labelRepository.findByDatasetOrderById(dataset)) {
            pairs.add(toPair(label));
        }
        return PairwiseEvaluator.evaluate(pairs);
    }

    private EvaluatedPair toPair(EvaluationLabel label) {
        long aId = label.getAlertAId();
        long bId = label.getAlertBId();
        Optional<Long> eventA = reader.findEventId(aId);
        Optional<Long> eventB = reader.findEventId(bId);
        boolean predictedSame = eventA.isPresent() && eventA.equals(eventB);

        Boolean goldInCandidates = null;
        if (label.getLabel() == EvaluationLabelType.SAME && !predictedSame
                && eventA.isPresent() && eventB.isPresent()) {
            goldInCandidates = goldEventInCandidates(aId, bId, eventA.get(), eventB.get());
        }
        return new EvaluatedPair(aId, bId, label.getLabel(), label.getStratum(), predictedSame, goldInCandidates);
    }

    /** 나중 알림을 b, 이른 알림을 a 로 보고 b 시점 top-3 후보에 a 의 이벤트가 있었는지. 계산 불가면 null. */
    private Boolean goldEventInCandidates(long idX, long idY, long eventX, long eventY) {
        Optional<EvaluationAssignmentReader.AlertSnapshot> x = reader.findSnapshot(idX);
        Optional<EvaluationAssignmentReader.AlertSnapshot> y = reader.findSnapshot(idY);
        if (x.isEmpty() || y.isEmpty() || !x.get().hasEmbedding() || !y.get().hasEmbedding()) {
            return null;
        }
        // 시각이 같으면 id 가 큰 쪽이 나중에 들어온 것으로 본다.
        int cmp = x.get().createdAt().compareTo(y.get().createdAt());
        boolean xIsLater = cmp > 0 || (cmp == 0 && idX > idY);
        long bId = xIsLater ? idX : idY;
        long goldEvent = xIsLater ? eventY : eventX;
        var bSnapshot = xIsLater ? x.get() : y.get();

        return reader.findTopCandidateEventIds(bId, bSnapshot.createdAt(), candidateTimeWindowHours)
                .contains(goldEvent);
    }
}
