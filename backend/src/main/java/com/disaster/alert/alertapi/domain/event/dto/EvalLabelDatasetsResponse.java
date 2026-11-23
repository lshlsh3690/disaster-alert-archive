package com.disaster.alert.alertapi.domain.event.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 화면 "평가 셋 라벨"의 셋 목록. 셋마다 이름·쌍 수·라벨별 개수·마지막으로 라벨을 고친 시각을 담는다.
 * 화면은 이 목록에서 셋을 고른 뒤 그 셋의 라벨 목록을 불러온다(목록 조회는 셋 이름이 필수).
 */
public record EvalLabelDatasetsResponse(List<Dataset> datasets) {

    /** 셋 하나. lastUpdatedAt 은 라벨을 한 번도 고친 적 없으면 null. */
    public record Dataset(String name, long pairs, long same, long diff, long unsure, LocalDateTime lastUpdatedAt) {
    }
}
