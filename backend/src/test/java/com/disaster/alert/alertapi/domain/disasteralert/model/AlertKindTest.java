package com.disaster.alert.alertapi.domain.disasteralert.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlertKindTest {

    /**
     * 결정: 모델은 실종 수배를 MISSING_PERSON 으로 답할 수 있지만 저장 분류에는 그 값이 없다.
     * 실종 수배는 사건이므로 INCIDENT 로 저장한다.
     * Red: 뼈대가 항상 null 을 돌려줘서 INCIDENT 기대와 달라 실패한다.
     */
    @Test
    @DisplayName("MISSING_PERSON 응답은 INCIDENT 로 저장한다")
    void missingPersonIsStoredAsIncident() {
        // 실행: 모델이 실종 수배 라벨로 답한 경우
        AlertKind kind = AlertKind.fromModelAnswer("MISSING_PERSON");

        // 검증: 실종 수배도 사건으로 합산(이전 평가와 동일)
        assertThat(kind).isEqualTo(AlertKind.INCIDENT);
    }

    /**
     * 결정: 분류 불가 응답은 임의 값으로 채우지 않고 null(미분류)로 둔다. 틀린 라벨보다 빈 값이 안전하다.
     * Red: 뼈대가 항상 null 이라 이미 통과한다(Green 에서 기본값을 잘못 채우는 회귀를 막는 가드).
     */
    @Test
    @DisplayName("알 수 없는 문자열·빈 문자열·null 은 null(분류 불가)")
    void unknownAnswerIsNull() {
        // 실행·검증: 모델 잡담, 빈 응답, 응답 없음 모두 저장하지 않는 값
        assertThat(AlertKind.fromModelAnswer("잘 모르겠습니다")).isNull();
        assertThat(AlertKind.fromModelAnswer("")).isNull();
        assertThat(AlertKind.fromModelAnswer(null)).isNull();
    }
}
