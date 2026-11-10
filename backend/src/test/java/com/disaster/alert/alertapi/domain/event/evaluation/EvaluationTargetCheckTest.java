package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.ReportedPair.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationTargetCheckTest {

    // 튜닝 전후를 비교할 때 평가 대상이 줄면(이벤트 미배정이 늘거나, 이전 틀린 쌍이 평가에서 빠지면) 틀린 쌍이 줄어도
    // 개선이 아니라 데이터가 빠진 것이다. 그런 실행은 실패 사유를 돌려주고, 대상이 그대로면 통과해야 한다.
    // 통과 판정을 같은 테스트에 넣은 이유: 통과만 보는 테스트는 뼈대(항상 empty)에서도 통과해 Red 가 되지 않는다.
    // Red 단계: 뼈대는 항상 Optional.empty() 를 돌려줘 두 실패 케이스의 isPresent 단언에서 실패한다.
    @Test
    void 평가_대상이_줄었으면_실패_사유를_돌려주고_그대로면_통과시킨다() {
        // 준비: 빠진 쌍이 없는 비교 결과와, 이전 틀린 쌍 2개가 평가에서 빠진 비교 결과
        ReportDiff.Result noDrop = new ReportDiff.Result(List.of(), List.of(), List.of(), List.of());
        ReportDiff.Result twoDropped = new ReportDiff.Result(List.of(), List.of(), List.of(),
                List.of(new ReportedPair(1, 2, Kind.MISSED), new ReportedPair(3, 4, Kind.MISSED)));

        // 실행: 미배정 3 → 7 로 늘어난 경우 / 미배정은 그대로(3)인데 2쌍이 빠진 경우 / 둘 다 그대로인 경우
        Optional<String> unassignedGrew = EvaluationTargetCheck.shrinkReason(3, 7, noDrop);
        Optional<String> droppedOut = EvaluationTargetCheck.shrinkReason(3, 3, twoDropped);
        Optional<String> unchanged = EvaluationTargetCheck.shrinkReason(3, 3, noDrop);

        // 검증: 미배정이 늘면 사유에 기준선·이번 숫자가 모두 들어간다(사용자가 얼마나 늘었는지 바로 보도록)
        assertThat(unassignedGrew).isPresent();
        assertThat(unassignedGrew.get()).contains("3").contains("7");
        // 검증: 빠진 쌍이 있으면 사유에 빠진 쌍의 수(2)가 들어간다
        assertThat(droppedOut).isPresent();
        assertThat(droppedOut.get()).contains("2");
        // 검증: 대상이 줄지 않았으면 통과(사유 없음)
        assertThat(unchanged).isEmpty();
    }
}
