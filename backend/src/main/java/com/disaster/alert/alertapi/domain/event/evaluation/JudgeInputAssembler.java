package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.AlertInput;
import com.disaster.alert.alertapi.domain.event.evaluation.AssignmentReader.AlertSnapshot;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge.JudgeAlert;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge.JudgeInput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 알림 두 건을 판사({@code ClusteringJudge}) 입력으로 조립한다. 판사 일치율 측정({@link JudgeAgreementRunner})과
 * 운영 일일 채점이 <b>같은 입력 형태</b>로 판사를 부르게 하려는 공용 부품이다 - 272쌍으로 잰 일치율을 운영 쌍에 붙이려면
 * 판사가 보는 입력이 같아야 하므로, 조립 코드를 두 곳에 따로 두지 않는다.
 *
 * <p>입력은 두 알림의 값과 규칙으로 계산한 사실뿐이다. 이벤트 배정은 넣지 않는다({@link JudgeInput} 에 그런 필드가 없다).
 */
@Component
@RequiredArgsConstructor
public class JudgeInputAssembler {

    private final AssignmentReader assignmentReader;
    private final PlaceDirectoryLoader placeDirectoryLoader;

    /**
     * 한 번의 실행(측정 한 번, 채점 하루)에서 쓸 조립기를 연다. 법정동·이웃 표와 지역 이름 표는 수백 행을 읽는
     * 조회라 실행마다 한 번만 읽어 {@link Session} 에 쥐어 둔다.
     */
    public Session open() {
        return new Session(new AlertPlaceFacts(placeDirectoryLoader.load()), assignmentReader.findSigunguNames());
    }

    /** 이름 표와 사실 계산기를 쥔 채 쌍마다 판사 입력을 만든다. 읽기만 하므로 상태가 변하지 않는다. */
    public static final class Session {
        private final AlertPlaceFacts facts;
        private final Map<String, String> regionNames;

        private Session(AlertPlaceFacts facts, Map<String, String> regionNames) {
            this.facts = facts;
            this.regionNames = regionNames;
        }

        /** 판사 입력: 두 알림의 값 + 규칙으로 계산한 사실. */
        public JudgeInput toInput(AlertSnapshot first, AlertSnapshot second) {
            AlertInput inputA = input(first);
            AlertInput inputB = input(second);
            return new JudgeInput(judgeAlert(first, inputA), judgeAlert(second, inputB), facts.compare(inputA, inputB));
        }

        private JudgeAlert judgeAlert(AlertSnapshot s, AlertInput input) {
            // 이름 표에 없는 코드는 코드 그대로 보인다 - 이름을 지어내는 것보다 낫다
            List<String> names = s.sigunguCodes().stream().map(code -> regionNames.getOrDefault(code, code)).toList();
            return new JudgeAlert(s.sentAt(), s.type(), names, s.message(), facts.analyze(input));
        }

        private static AlertInput input(AlertSnapshot s) {
            return new AlertInput(s.message(), s.sentAt(), s.type(), s.sigunguCodes());
        }
    }
}
