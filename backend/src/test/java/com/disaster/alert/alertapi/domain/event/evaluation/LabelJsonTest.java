package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.LabeledPair;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LabelJsonTest {

    // 항목 하나를 사람이 보는 필드(q, relation, gap, at, type, region, message)까지 채워 만든다.
    // 파서가 이 필드들을 건드리면 안 되는지 보려고 일부러 전부 넣는다.
    private static String item(int q, long a, long b, String label) {
        return """
                {"q":%d,"label":"%s","relation":"같은 시군구","gap":"0시간 3분",
                 "a":{"id":%d,"at":"2025-07-17 10:00","type":"호우","region":"충남 서산시","message":"원문 A"},
                 "b":{"id":%d,"at":"2025-07-17 10:03","type":"호우","region":"충남 서산시","message":"원문 B"}}
                """.formatted(q, label, a, b);
    }

    // 사람용 필드가 잔뜩 붙은 JSON 에서도 평가에 필요한 값(a.id, b.id, label)만 뽑아 입력 순서대로 담는다.
    // Red 단계: 뼈대는 빈 목록을 돌려주므로 "쌍 3개" 기대에서 실패한다.
    @Test
    void 배열을_입력_순서대로_쌍_목록으로_바꾼다() {
        // 준비: 세 항목 — SAME/DIFF/UNSURE 를 하나씩, 일부러 id 순서를 뒤섞어 정렬되지 않는지도 본다
        String json = "[" + item(1, 45936, 45937, "SAME") + "," + item(2, 900, 100, "DIFF") + ","
                + item(3, 7, 8, "UNSURE") + "]";

        // 실행
        List<LabeledPair> pairs = LabelJson.parse(json);

        // 검증: 입력 순서 그대로, a/b 도 뒤집히지 않는다(UNSURE 도 여기서는 버리지 않고 담긴다)
        assertThat(pairs).containsExactly(
                new LabeledPair(45936, 45937, Label.SAME),
                new LabeledPair(900, 100, Label.DIFF),
                new LabeledPair(7, 8, Label.UNSURE));
    }

    // SAME/DIFF/UNSURE 가 아닌 라벨(오타·빈칸)을 조용히 건너뛰면 평가 숫자가 슬그머니 달라진다.
    // 그래서 즉시 실패하고, 고칠 수 있게 몇 번째 항목인지 알려 준다(배열 첫 항목이 1번째).
    // Red 단계: 뼈대는 예외 없이 빈 목록을 돌려주므로 "예외가 없다" 로 실패한다.
    @Test
    void 알_수_없는_label은_항목_번호와_함께_예외() {
        // 준비: 2번째 항목의 label 만 오타(MAYBE)
        String json = "[" + item(1, 1, 2, "SAME") + "," + item(2, 3, 4, "MAYBE") + "]";

        // 실행 + 검증: IllegalArgumentException 이고, 메시지가 2번째 항목을 지목한다
        assertThatThrownBy(() -> LabelJson.parse(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2번째 항목");
    }

    // 같은 쌍을 (a,b) 와 (b,a) 로 두 번 적으면 한 쌍이 두 번 세어져 점수가 틀어진다.
    // 순서와 무관하게 같은 쌍으로 보고, 뒤쪽 항목을 지목해 예외를 던진다.
    // Red 단계: 뼈대는 예외 없이 빈 목록을 돌려주므로 "예외가 없다" 로 실패한다.
    @Test
    void 순서만_뒤집은_중복_쌍은_뒤쪽_항목을_지목해_예외() {
        // 준비: 1번째 항목의 (1,2) 를 3번째 항목이 (2,1) 로 뒤집어 다시 적음, 사이에 무관한 쌍 하나
        String json = "[" + item(1, 1, 2, "SAME") + "," + item(2, 3, 4, "DIFF") + "," + item(3, 2, 1, "SAME") + "]";

        // 실행 + 검증: 먼저 나온 1번째가 아니라 중복을 만든 3번째 항목이 지목된다
        assertThatThrownBy(() -> LabelJson.parse(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3번째 항목");
    }
}
