package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.AssignmentReader.PairFacts;
import com.disaster.alert.alertapi.domain.event.evaluation.PairCountResult.WrongKind;
import com.disaster.alert.alertapi.domain.event.evaluation.ReportFormatter.Row;
import com.disaster.alert.alertapi.domain.event.evaluation.ReportedPair.Kind;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    // 틀린 쌍 절 제목이 없는 마크다운(형식이 다른 옛 리포트나 엉뚱한 파일)을 기준선으로 주면, 빈 목록을 돌려주는 순간
    // 이번 실행의 틀린 쌍이 전부 "새로 틀림" 으로 집계된다 — 퇴행을 찾는 도구가 전부 퇴행이라고 말하게 된다.
    // 그래서 조용히 비우지 않고 예외로 알려야 한다. 틀린 쌍이 0개인 정상 기준선은 제목은 써지므로 이 경로에 걸리지 않는다.
    // Red 단계: 현재 구현은 제목을 못 찾으면 빈 목록을 돌려줘 예외가 안 나므로 assertThatThrownBy 가 실패한다.
    @Test
    void 틀린_쌍_절_제목이_없으면_빈_목록_대신_예외를_던진다() {
        // 준비: backend/evaluation/reports/before.md 처럼 "## 오류 사례" 4칸 표만 있는 형식이 다른 옛 리포트
        String otherFormat = """
                # 클러스터링 평가 - before
                ## 요약
                - 같은 사건 110쌍 중 69쌍 묶음
                ## 오류 사례
                | alertA | alertB | 유형 | 비고 |
                |---|---|---|---|
                | 43443 | 44576 | 화재 | 놓침 |
                """;

        // 실행 + 검증: 틀린 쌍 절이 아예 없으므로 기준선으로 쓸 수 없다고 예외로 알린다
        assertThatThrownBy(() -> BaselineReportParser.parse(otherFormat))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 포매터가 쓴 제목과 파서가 찾는 제목은 지금 각자 문자열로 박혀 있어 우연히 같을 뿐이다. 한쪽만 고치면 파서가 조용히
    // 틀린 쌍을 못 읽게 되므로, 포매터 출력을 파서로 다시 읽어 같은 쌍이 나오는지로 두 문자열이 계속 맞는지 지킨다.
    // 참고: 이 테스트는 Red 단계에서도 통과하는 회귀 방지용이다(실제 Red 는 위 예외 테스트).
    @Test
    void 포매터가_만든_리포트를_파서가_그대로_다시_읽는다() {
        // 준비: 놓침 1건과 잘못 묶음 1건. 두 종류가 모두 되돌아오는지 보려고 각각 하나씩 넣는다
        Row missed = new Row(301, 302, WrongKind.MISSED, new PairFacts("산불", "산불", true, 5, 0.2));
        Row overMerged = new Row(401, 402, WrongKind.OVER_MERGED, new PairFacts("산불", "호우", false, 30, null));
        PairCountResult result = new PairCountResult(2, 1, 1, 0, 0, OptionalDouble.of(33.3), OptionalDouble.of(33.3), List.of());

        // 실행: 리포트를 만들어 문자열로 받은 것을 그대로 파서에 다시 먹인다
        String report = ReportFormatter.format("ds", LocalDateTime.of(2026, 11, 7, 10, 0), result, List.of(missed, overMerged));
        List<ReportedPair> pairs = BaselineReportParser.parse(report);

        // 검증: 넣은 id 쌍과 종류가 순서대로 그대로 나온다
        assertThat(pairs).containsExactly(
                new ReportedPair(301, 302, Kind.MISSED),
                new ReportedPair(401, 402, Kind.OVER_MERGED));
    }
}
