package com.disaster.alert.alertapi.domain.disasteralert.model;

import java.util.List;

/**
 * 사건/비사건 분류기를 사람 라벨과 대조해 센다. 순수 로직.
 * 사건을 비사건으로 분류하면 그 알림이 클러스터링에서 빠지고, 비사건을 사건으로 분류하면 클러스터링에 들어간다 —
 * 결과가 다르므로 두 오류를 각자의 분모로 따로 센다.
 */
public final class AlertKindScore {

    private AlertKindScore() {
    }

    /** 한 알림의 사람 라벨과 분류기 답. predicted 가 null 이면 분류 실패(미분류). */
    public record Item(long alertId, AlertKind human, AlertKind predicted) {
    }

    /**
     * @param incidents          사람이 사건이라 한 알림 수 (분류 실패 제외)
     * @param incidentAsAdvisory 그중 비사건으로 분류된 알림 id
     * @param advisories         사람이 비사건이라 한 알림 수 (분류 실패 제외)
     * @param advisoryAsIncident 그중 사건으로 분류된 알림 id
     * @param failed             분류 실패(predicted null) 알림 id — 두 분모 어디에도 넣지 않는다
     */
    public record Result(int incidents, List<Long> incidentAsAdvisory,
                         int advisories, List<Long> advisoryAsIncident,
                         List<Long> failed) {
    }

    // Red 단계 뼈대 — 구현은 Green 에서
    public static Result score(List<Item> items) {
        return new Result(0, List.of(), 0, List.of(), List.of());
    }
}
