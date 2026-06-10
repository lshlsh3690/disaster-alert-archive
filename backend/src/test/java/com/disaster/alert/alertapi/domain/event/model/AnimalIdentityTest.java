package com.disaster.alert.alertapi.domain.event.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 종 추출기 단위 테스트 — cross-region 종 하드게이트의 정확성 고정.
 * 케이스는 운영 데이터의 실제 재난문자에서 발췌.
 */
class AnimalIdentityTest {

    @Test
    @DisplayName("관측된 종을 본문에서 식별한다")
    void identifiesObservedSpecies() {
        assertThat(AnimalIdentity.species("방학동 514-2 인근 야생멧돼지 출몰 신고")).isEqualTo("멧돼지");
        assertThat(AnimalIdentity.species("대전 오월드 늑대 탈출 수색 중")).isEqualTo("늑대");
        assertThat(AnimalIdentity.species("광명시 옥길동 사슴 5마리 탈출")).isEqualTo("사슴");
        assertThat(AnimalIdentity.species("석포리 곰사육농장 곰1마리 탈출")).isEqualTo("곰");
        assertThat(AnimalIdentity.species("자인면 농장에서 소 10마리 탈출로 포획중")).isEqualTo("소");
        assertThat(AnimalIdentity.species("대동 신흥동 인근 대형견 출몰")).isEqualTo("들개");
    }

    @Test
    @DisplayName("늑대와 멧돼지는 서로 다른 종으로 식별된다 — 과병합 차단의 근거")
    void wolfAndBoarAreDistinct() {
        String wolf = AnimalIdentity.species("오월드 늑대 탈출");
        String boar = AnimalIdentity.species("야생멧돼지 출몰");
        assertThat(wolf).isNotEqualTo(boar);
        assertThat(AnimalIdentity.speciesRegex(wolf)).isNotEqualTo(AnimalIdentity.speciesRegex(boar));
    }

    @Test
    @DisplayName("사전에 없는 본문은 null — cross-region 안 함(보수적)")
    void returnsNullForUnknown() {
        assertThat(AnimalIdentity.species("물놀이 안전사고 예방 안전수칙 안내")).isNull();
        assertThat(AnimalIdentity.species("교통사고로 도로 전면 차단")).isNull();
        assertThat(AnimalIdentity.species(null)).isNull();
        assertThat(AnimalIdentity.speciesRegex(null)).isNull();
    }

    @Test
    @DisplayName("speciesRegex 는 식별된 종의 별칭 포함 정규식을 돌려준다")
    void speciesRegexRoundTrip() {
        assertThat(AnimalIdentity.speciesRegex("들개")).isEqualTo("들개|대형견|유기견");
        assertThat(AnimalIdentity.speciesRegex("멧돼지")).isEqualTo("멧돼지");
    }
}
