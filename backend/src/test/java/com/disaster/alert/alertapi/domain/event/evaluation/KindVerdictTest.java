package com.disaster.alert.alertapi.domain.event.evaluation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class KindVerdictTest {

    // 결정: 모델이 줄바꿈·소문자를 섞어 답해도 단어가 정확히 INCIDENT/ADVISORY 면 받는다.
    // Red 단계: 뼈대가 항상 null 을 돌려줘서 "expected INCIDENT but was null" 로 실패한다.
    @Test
    @DisplayName("앞뒤 공백·대소문자는 무시하고 INCIDENT/ADVISORY 로 읽는다")
    void ignoresCaseAndSurroundingWhitespace() {
        assertThat(KindVerdict.fromModelAnswer("  incident\n")).isEqualTo(KindVerdict.INCIDENT);
        assertThat(KindVerdict.fromModelAnswer("\tAdvisory ")).isEqualTo(KindVerdict.ADVISORY);
    }

    // 결정: 판사는 두 단어만 쓴다. 분류기(AlertKind)와 달리 MISSING_PERSON 도 받지 않는다 —
    // 판사 프롬프트는 두 단어만 약속하므로 그 밖의 답은 판정 불가로 따로 세야 한다.
    // 뼈대가 이미 null 이라 Red 에서도 통과한다 — Green 이 AlertKind 를 재사용해 접으면 깨지는 회귀 방지용.
    @ParameterizedTest(name = "[{index}] \"{0}\" -> 판정 불가")
    @NullAndEmptySource
    @ValueSource(strings = {
            "MISSING_PERSON",           // 분류기는 INCIDENT 로 접지만 판사는 받지 않는다
            "NOT INCIDENT",             // 부정문
            "INCIDENT or ADVISORY",     // 두 답이 섞인 양다리
            "ADVISORY.",                // 마침표가 붙은 답
            "사건"                       // 한국어 풀이
    })
    @DisplayName("MISSING_PERSON·부정문·여러 단어·마침표·한국어·빈 답은 null(판정 불가)")
    void rejectsAnythingButExactToken(String answer) {
        assertThat(KindVerdict.fromModelAnswer(answer)).isNull();
    }
}
