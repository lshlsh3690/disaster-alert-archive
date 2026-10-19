package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelCsv.Label;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelCsv.LabeledPair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LabelCsvTest {

    private static final String HEADER = "alert_a_id,alert_b_id,label,stratum,sub_type";

    // 형식이 틀린 CSV 는 조용히 건너뛰면 평가 숫자가 슬그머니 달라진다. 그래서 즉시 실패하고,
    // 사용자가 고칠 수 있게 "몇 번째 줄" 인지 메시지에 넣는다(헤더가 1번째 줄).
    // Red 단계: 뼈대는 예외 없이 빈 목록을 돌려주므로 assertThatThrownBy 에서 "예외가 없다" 로 실패한다.
    @ParameterizedTest(name = "{0}")
    @MethodSource("badCsvs")
    void 형식_오류는_줄_번호와_함께_예외(String caseName, String csv, int errorLine) {
        // 준비: 케이스별로 errorLine 번째 줄에 오류가 있는 CSV (badCsvs 참고)
        // 실행 + 검증: IllegalArgumentException 이고 메시지에 "N번째 줄" 이 있어야 한다
        assertThatThrownBy(() -> LabelCsv.parse(csv))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(errorLine + "번째 줄");
    }

    static Stream<Arguments> badCsvs() {
        return Stream.of(
                // 헤더가 약속과 다름(컬럼 이름 오타) → 1번째 줄
                Arguments.of("헤더 불일치", "a,b,label,stratum,sub_type\n1,2,SAME,x,y\n", 1),
                // 3번째 줄만 컬럼이 4개
                Arguments.of("컬럼 수 오류", HEADER + "\n1,2,SAME,x,y\n3,4,SAME,x\n", 3),
                // 2번째 줄의 alert_a_id 가 숫자가 아님
                Arguments.of("숫자 아님", HEADER + "\nabc,2,SAME,x,y\n", 2),
                // 자기 자신과의 쌍은 정답을 가릴 수 없다
                Arguments.of("같은 id", HEADER + "\n5,5,DIFF,x,y\n", 2),
                // SAME/DIFF/UNSURE 외의 라벨
                Arguments.of("알 수 없는 label", HEADER + "\n1,2,MAYBE,x,y\n", 2),
                // 2번째 줄의 (1,2) 를 4번째 줄이 (2,1) 로 뒤집어 다시 적음 → 중복은 뒤쪽 줄을 지목
                Arguments.of("(a,b)/(b,a) 중복", HEADER + "\n1,2,SAME,x,y\n3,4,DIFF,x,y\n2,1,SAME,x,y\n", 4)
        );
    }

    // 엑셀 등이 저장한 CSV 는 BOM(﻿)이 앞에 붙고 중간에 빈 줄이 끼기도 한다. 이건 오류가 아니라 정상 입력이다.
    // Red 단계: 뼈대는 빈 목록을 돌려주므로 "쌍 2개" 기대에서 실패한다.
    @Test
    void BOM과_빈_줄은_허용하고_정상_파싱() {
        // 준비: BOM 으로 시작하고, 데이터 줄 사이와 끝에 빈 줄이 있는 CSV
        String csv = "﻿" + HEADER + "\n1,2,SAME,s,t\n\n3,4,UNSURE,s,t\n\n";

        // 실행
        List<LabeledPair> pairs = LabelCsv.parse(csv);

        // 검증: 빈 줄은 건너뛰고 쌍 2개만 나온다(UNSURE 도 여기서는 버리지 않고 그대로 담긴다)
        assertThat(pairs).containsExactly(
                new LabeledPair(1, 2, Label.SAME),
                new LabeledPair(3, 4, Label.UNSURE));
    }
}
