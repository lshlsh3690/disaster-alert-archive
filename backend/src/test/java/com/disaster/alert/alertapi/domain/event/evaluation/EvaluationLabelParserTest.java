package com.disaster.alert.alertapi.domain.event.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvaluationLabelParserTest {

    private static final String HEADER = "alert_a_id,alert_b_id,label,stratum,sub_type";

    private static String csv(String... rows) {
        return HEADER + "\n" + String.join("\n", rows) + "\n";
    }

    private static void assertRejected(String text, String... expectedInMessage) {
        assertThatThrownBy(() -> EvaluationLabelParser.parse(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContainingAll(expectedInMessage);
    }

    @Test
    @DisplayName("헤더와 여러 행을 순서대로 레코드로 변환한다")
    void parsesRowsInOrder() {
        List<EvaluationLabelRecord> result = EvaluationLabelParser.parse(csv(
                "1,2,SAME,GENERAL,general_same",
                "3,4,DIFF,BOUNDARY,boundary_diff",
                "5,6,UNSURE,TRAP,trap_x"));

        assertThat(result).containsExactly(
                new EvaluationLabelRecord(1, 2, EvaluationLabelType.SAME, EvaluationStratum.GENERAL, "general_same"),
                new EvaluationLabelRecord(3, 4, EvaluationLabelType.DIFF, EvaluationStratum.BOUNDARY, "boundary_diff"),
                new EvaluationLabelRecord(5, 6, EvaluationLabelType.UNSURE, EvaluationStratum.TRAP, "trap_x"));
    }

    @Test
    @DisplayName("label/stratum 은 대소문자를 구분하지 않는다")
    void ignoresCase() {
        List<EvaluationLabelRecord> result = EvaluationLabelParser.parse(csv(
                "1,2,same,general,a",
                "3,4,Diff,Boundary,b",
                "5,6,unsure,Trap,c"));

        assertThat(result).extracting(EvaluationLabelRecord::label)
                .containsExactly(EvaluationLabelType.SAME, EvaluationLabelType.DIFF, EvaluationLabelType.UNSURE);
        assertThat(result).extracting(EvaluationLabelRecord::stratum)
                .containsExactly(EvaluationStratum.GENERAL, EvaluationStratum.BOUNDARY, EvaluationStratum.TRAP);
    }

    @Test
    @DisplayName("필드 앞뒤 공백을 trim 한다")
    void trimsWhitespace() {
        List<EvaluationLabelRecord> result = EvaluationLabelParser.parse(
                HEADER + "\n 1 , 2 , SAME , GENERAL , general_same \n");

        assertThat(result).containsExactly(
                new EvaluationLabelRecord(1, 2, EvaluationLabelType.SAME, EvaluationStratum.GENERAL, "general_same"));
    }

    @Test
    @DisplayName("CRLF 줄바꿈을 처리한다")
    void handlesCrlf() {
        List<EvaluationLabelRecord> result = EvaluationLabelParser.parse(
                HEADER + "\r\n1,2,SAME,GENERAL,a\r\n3,4,DIFF,TRAP,b\r\n");

        assertThat(result).hasSize(2);
        assertThat(result.get(1).subType()).isEqualTo("b");
    }

    @Test
    @DisplayName("UTF-8 BOM 으로 시작해도 헤더를 인식한다")
    void handlesBom() {
        List<EvaluationLabelRecord> result = EvaluationLabelParser.parse("﻿" + csv("1,2,SAME,GENERAL,a"));

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("빈 줄과 끝 개행은 무시한다")
    void ignoresBlankLines() {
        List<EvaluationLabelRecord> result = EvaluationLabelParser.parse(
                HEADER + "\n\n1,2,SAME,GENERAL,a\n\n3,4,DIFF,TRAP,b\n\n\n");

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("UNSURE 도 걸러내지 않고 레코드로 유지한다")
    void keepsUnsure() {
        List<EvaluationLabelRecord> result = EvaluationLabelParser.parse(csv("1,2,UNSURE,GENERAL,a"));

        assertThat(result).singleElement()
                .extracting(EvaluationLabelRecord::label).isEqualTo(EvaluationLabelType.UNSURE);
    }

    @Test
    @DisplayName("실제 데이터셋 형식의 한 줄을 파싱한다")
    void parsesRealFormatLine() {
        List<EvaluationLabelRecord> result = EvaluationLabelParser.parse(
                csv("43443,44576,SAME,TRAP,trap_same_place_other_time"));

        assertThat(result).containsExactly(new EvaluationLabelRecord(
                43443L, 44576L, EvaluationLabelType.SAME, EvaluationStratum.TRAP, "trap_same_place_other_time"));
    }

    @Test
    @DisplayName("데이터 행이 없으면(헤더만) 빈 리스트 - 오류 아님")
    void headerOnlyIsEmpty() {
        assertThat(EvaluationLabelParser.parse(HEADER + "\n")).isEmpty();
        assertThat(EvaluationLabelParser.parse(HEADER)).isEmpty();
    }

    @Test
    @DisplayName("빈 문자열/null 입력은 거부한다")
    void rejectsEmptyOrNull() {
        assertRejected("");
        assertRejected("   \n");
        assertThatThrownBy(() -> EvaluationLabelParser.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("헤더가 다르면 1번째 줄 오류로 거부한다")
    void rejectsBadHeader() {
        assertRejected("a,b,c,d,e\n1,2,SAME,GENERAL,x\n", "1");
        assertRejected("alert_b_id,alert_a_id,label,stratum,sub_type\n1,2,SAME,GENERAL,x\n", "1");
        assertRejected("1,2,SAME,GENERAL,x\n", "1");
    }

    @Test
    @DisplayName("컬럼 수가 다르면 해당 줄 번호와 함께 거부한다")
    void rejectsColumnCountMismatch() {
        assertRejected(csv("1,2,SAME,GENERAL,a", "3,4,DIFF,TRAP"), "3");
        assertRejected(csv("1,2,SAME,GENERAL,a,extra"), "2");
    }

    @Test
    @DisplayName("숫자가 아닌 id 는 줄 번호와 함께 거부한다")
    void rejectsNonNumericId() {
        assertRejected(csv("1,2,SAME,GENERAL,a", "x,4,DIFF,TRAP,b"), "3");
        assertRejected(csv("1,y,SAME,GENERAL,a"), "2");
    }

    @Test
    @DisplayName("0 이하 id 는 거부한다")
    void rejectsNonPositiveId() {
        assertRejected(csv("0,2,SAME,GENERAL,a"), "2");
        assertRejected(csv("1,2,SAME,GENERAL,a", "3,-4,DIFF,TRAP,b"), "3");
    }

    @Test
    @DisplayName("alert_a_id 와 alert_b_id 가 같으면 거부한다")
    void rejectsSameIds() {
        assertRejected(csv("7,7,SAME,GENERAL,a"), "2");
    }

    @Test
    @DisplayName("알 수 없는 label 은 거부한다")
    void rejectsUnknownLabel() {
        assertRejected(csv("1,2,MAYBE,GENERAL,a"), "2");
    }

    @Test
    @DisplayName("알 수 없는 stratum 은 거부한다")
    void rejectsUnknownStratum() {
        assertRejected(csv("1,2,SAME,EXTRA,a"), "2");
    }

    @Test
    @DisplayName("빈 sub_type 은 거부한다")
    void rejectsBlankSubType() {
        assertRejected(csv("1,2,SAME,GENERAL,"), "2");
        assertRejected(csv("1,2,SAME,GENERAL,   "), "2");
    }

    @Test
    @DisplayName("같은 (a,b) 쌍이 두 번 나오면 두 줄 번호와 함께 거부한다")
    void rejectsDuplicatePair() {
        assertRejected(csv("1,2,SAME,GENERAL,a", "3,4,DIFF,TRAP,b", "1,2,DIFF,TRAP,c"), "2", "4");
    }

    @Test
    @DisplayName("순서만 뒤집힌 (b,a) 도 같은 쌍으로 보고 거부한다")
    void rejectsReversedDuplicatePair() {
        assertRejected(csv("1,2,SAME,GENERAL,a", "2,1,SAME,GENERAL,b"), "2", "3");
    }
}
