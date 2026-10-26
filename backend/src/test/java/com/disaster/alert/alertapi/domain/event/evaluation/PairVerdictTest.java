package com.disaster.alert.alertapi.domain.event.evaluation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PairVerdictTest {

    // 결정: 모델이 줄바꿈·소문자를 섞어 답해도 단어가 정확히 SAME/DIFF 면 받는다.
    // Red 단계: 뼈대가 항상 null 을 돌려줘서 "expected SAME but was null" 로 실패한다.
    @Test
    @DisplayName("앞뒤 공백·대소문자는 무시하고 SAME/DIFF 로 읽는다")
    void ignoresCaseAndSurroundingWhitespace() {
        // 모델이 뒤에 줄바꿈을 붙이거나 소문자로 답하는 흔한 경우
        assertThat(PairVerdict.fromModelAnswer("  same\n")).isEqualTo(PairVerdict.SAME);
        assertThat(PairVerdict.fromModelAnswer("\tDiff ")).isEqualTo(PairVerdict.DIFF);
    }

    // 결정: 단어가 정확히 일치하지 않으면 추측하지 않고 null(판사 판정 불가)로 둔다.
    // contains 로 읽으면 부정문에서 같다/다르다가 뒤집혀 신뢰도 숫자가 오염된다.
    // 뼈대가 이미 null 이라 Red 에서도 통과한다 — Green 이 contains 로 구현하면 깨지게 고정하는 회귀 방지용.
    @ParameterizedTest(name = "[{index}] \"{0}\" -> 판정 불가")
    @NullAndEmptySource
    @ValueSource(strings = {
            "NOT SAME",          // 부정문: SAME 토큰이 들어 있어도 뜻은 반대
            "SAME or DIFF",      // 두 답이 섞인 양다리
            "DIFF.",             // 마침표가 붙은 답도 정해진 한 단어가 아니다
            "같은 사건",           // 한국어 풀이는 약속한 토큰이 아니다
            "잘 모르겠습니다"       // 잡담
    })
    @DisplayName("부정문·여러 단어·마침표·한국어·빈 답은 null(판정 불가)")
    void rejectsAnythingButExactToken(String answer) {
        assertThat(PairVerdict.fromModelAnswer(answer)).isNull();
    }
}
