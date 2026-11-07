package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.ReportedPair.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BaselineReportParserTest {

    // 새 리포트(7칸)는 맨 끝에 "종류" 칸이 있다. 유형 칸에 슬래시("기타/강풍")가 있어도, 거리 칸이 "-" 여도
    // 알림 id 두 개와 종류를 제대로 읽어야 이전 리포트와 비교할 수 있다.
    // Red 단계: 뼈대는 빈 리스트를 돌려줘 기대한 3건이 없으므로 실패한다.
    @Test
    void 일곱칸_표에서_틀린_쌍과_종류를_읽는다() {
        // 준비: 놓침 2건(슬래시 유형, 거리 "-" 포함)과 잘못 묶음 1건. 표 밖의 요약 줄은 읽으면 안 된다.
        String report = """
                # 클러스터링 평가 - cluster-eval-2026-10
                - 실행 시각: 2026-11-07 10:00

                ## 요약
                - 같은 사건 110쌍 중 69쌍 묶음, 41쌍 놓침 → 과소병합율 37.3%
                ## 놓친 쌍 / 잘못 묶은 쌍
                | alertA | alertB | 유형 | 같은 시군구 | 간격(시간) | 본문 거리 | 종류 |
                |---|---|---|---|---|---|---|
                | 43443 | 44576 | 화재 | 예 | 25 | 0.169 | 놓침 |
                | 46067 | 45887 | 기타/강풍 | 예 | 2 | 0.559 | 잘못 묶음 |
                | 201 | 202 | 산불/호우 | 아니오 | 30 | - | 놓침 |
                """;

        // 실행
        List<ReportedPair> pairs = BaselineReportParser.parse(report);

        // 검증: 표 순서대로 3건, 종류 칸의 한글 값이 enum 으로 바뀐다
        assertThat(pairs).containsExactly(
                new ReportedPair(43443, 44576, Kind.MISSED),
                new ReportedPair(46067, 45887, Kind.OVER_MERGED),
                new ReportedPair(201, 202, Kind.MISSED));
    }

    // 이미 커밋된 옛 리포트(2026-10-19)는 "종류" 칸이 없는 6칸 표다. 이것을 기준선으로 쓸 수 있어야 하는데,
    // 종류를 추측해 채우면(예: 전부 놓침) 가짜 정보가 되므로 UNKNOWN 으로 둔다.
    // Red 단계: 뼈대는 빈 리스트를 돌려줘 기대한 2건이 없으므로 실패한다.
    @Test
    void 여섯칸_옛_리포트는_종류가_UNKNOWN이다() {
        // 준비: backend/evaluation/reports/2026-10_2026-10-19.md 와 같은 6칸 형식
        String oldReport = """
                # 클러스터링 평가 - cluster-eval-2026-10
                - 실행 시각: 2026-10-19 17:36

                ## 요약
                - 같은 사건 110쌍 중 69쌍 묶음, 41쌍 놓침 → 과소병합율 37.3%
                ## 놓친 쌍 / 잘못 묶은 쌍
                | alertA | alertB | 유형 | 같은 시군구 | 간격(시간) | 본문 거리 |
                |---|---|---|---|---|---|
                | 43443 | 44576 | 화재 | 예 | 25 | 0.169 |
                | 201 | 202 | 산불/호우 | 아니오 | 30 | - |
                """;

        // 실행
        List<ReportedPair> pairs = BaselineReportParser.parse(oldReport);

        // 검증: id 는 읽히고 종류만 UNKNOWN
        assertThat(pairs).containsExactly(
                new ReportedPair(43443, 44576, Kind.UNKNOWN),
                new ReportedPair(201, 202, Kind.UNKNOWN));
    }
}
