package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.ReportDiff.KindChange;
import com.disaster.alert.alertapi.domain.event.evaluation.ReportedPair.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportDiffTest {

    // 같은 셋을 반복해 쓰므로 비율보다 "어느 쌍이 고쳐졌고 어느 쌍이 새로 틀렸는지" 가 더 민감한 신호다.
    // 쌍은 순서 무관으로 같다고 봐야 한다 — 리포트마다 (a,b) 순서가 달라질 수 있어 순서까지 비교하면
    // 안 바뀐 쌍이 "고쳐짐 + 새로 틀림" 으로 둘 다 잡힌다.
    // Red 단계: 뼈대는 세 목록을 모두 비워 돌려줘 fixed 기대(1건)에서 실패한다.
    @Test
    void 고쳐짐_새로_틀림_종류_바뀜을_가르고_쌍은_순서_무관으로_같다고_본다() {
        // 준비: 이전 리포트의 틀린 쌍
        List<ReportedPair> previous = List.of(
                new ReportedPair(1, 2, Kind.MISSED),        // 이번에 없음 → 고쳐짐
                new ReportedPair(10, 20, Kind.MISSED),      // 이번엔 (20,10) 순서로 있음 → 같은 쌍, 변화 없음
                new ReportedPair(5, 6, Kind.MISSED),        // 이번엔 (6,5) 가 잘못 묶음 → 종류 바뀜
                new ReportedPair(7, 8, Kind.UNKNOWN));      // 옛 6칸 리포트 → 종류를 모르니 바뀜으로 보지 않는다
        // 준비: 이번 틀린 쌍
        List<ReportedPair> current = List.of(
                new ReportedPair(20, 10, Kind.MISSED),
                new ReportedPair(6, 5, Kind.OVER_MERGED),
                new ReportedPair(7, 8, Kind.MISSED),
                new ReportedPair(3, 4, Kind.OVER_MERGED));  // 이전에 없음 → 새로 틀림

        // 실행
        ReportDiff.Result diff = ReportDiff.compare(previous, current);

        // 검증: 고쳐진 쌍은 이전 쪽 표기, 새로 틀린 쌍과 종류 바뀜은 이번 쪽 표기를 쓴다
        assertThat(diff.fixed()).containsExactly(new ReportedPair(1, 2, Kind.MISSED));
        assertThat(diff.newlyWrong()).containsExactly(new ReportedPair(3, 4, Kind.OVER_MERGED));
        assertThat(diff.kindChanged()).containsExactly(new KindChange(6, 5, Kind.MISSED, Kind.OVER_MERGED));
    }
}
