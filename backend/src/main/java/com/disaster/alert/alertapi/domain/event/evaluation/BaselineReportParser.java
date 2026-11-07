package com.disaster.alert.alertapi.domain.event.evaluation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 이전에 만든 평가 리포트(마크다운)에서 틀린 쌍 목록을 다시 읽는다.
 * 6칸(옛 형식)과 7칸("종류" 칸 추가) 표를 모두 읽는다.
 */
// Red 단계 뼈대 — 구현은 Green 에서
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BaselineReportParser {

    public static List<ReportedPair> parse(String reportMarkdown) {
        return List.of();
    }
}
