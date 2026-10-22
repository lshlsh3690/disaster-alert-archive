package com.disaster.alert.alertapi.domain.disasteralert.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlertKindTest {

    // 결정: 모델이 실종 수배를 MISSING_PERSON 으로 답해도 저장 값은 둘뿐이라 INCIDENT 로 접는다.
    @Test
    @DisplayName("MISSING_PERSON 응답은 INCIDENT 로 저장한다")
    void missingPersonIsStoredAsIncident() {
        assertThat(AlertKind.fromModelAnswer("MISSING_PERSON")).isEqualTo(AlertKind.INCIDENT);
    }

    // 결정: 분류 불가 응답(잡담·빈 문자열·null)은 추측해 채우지 않고 null(미분류)로 둔다.
    @Test
    @DisplayName("알 수 없는 문자열·빈 문자열·null 은 null(분류 불가)")
    void unknownAnswerIsNull() {
        assertThat(AlertKind.fromModelAnswer("잘 모르겠습니다")).isNull();
        assertThat(AlertKind.fromModelAnswer("")).isNull();
        assertThat(AlertKind.fromModelAnswer(null)).isNull();
    }

    // 결정: 앞뒤 공백·대소문자만 무시하고 한 단어가 정확히 일치할 때만 분류한다(contains 금지).
    // 문장 속 토큰을 읽으면 부정문·양다리 답에서 사건/비사건이 뒤집힌다.
    @Test
    @DisplayName("한 단어 토큰과 정확히 일치할 때만 분류하고, 문장 속 토큰은 null")
    void classifiesOnlyExactSingleToken() {
        assertThat(AlertKind.fromModelAnswer("  incident ")).isEqualTo(AlertKind.INCIDENT);

        assertThat(AlertKind.fromModelAnswer("INCIDENT 가 아니다")).isNull();
        assertThat(AlertKind.fromModelAnswer("ADVISORY (not an INCIDENT)")).isNull();
        assertThat(AlertKind.fromModelAnswer("INCIDENT or ADVISORY")).isNull();
        assertThat(AlertKind.fromModelAnswer("NON-INCIDENT")).isNull();
    }
}
