package com.disaster.alert.alertapi.domain.event.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 하루치 사건 알림을 판사(LLM)로 채점해 {@code clustering_daily_score}·{@code clustering_daily_score_pair} 에 남긴다
 * (설계: specs/006 plan 4절).
 *
 * <p>Red 단계 뼈대 - 판정 대상 쌍 선택·판사 호출·집계·저장은 Green 에서 쓴다. 지금은 아무것도 저장하지 않는다.
 */
@Service
public class ClusteringDailyScoreService {

    /**
     * 그날(created_at 기준) 발송된 사건 알림을 채점해 저장한다.
     *
     * @param date 채점할 날
     */
    public void score(LocalDate date) {
        // Red 단계 뼈대 — 구현은 Green 에서
    }
}
