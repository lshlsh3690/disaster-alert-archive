package com.disaster.alert.alertapi.domain.event.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 안내 이벤트 제목 라벨 검증 — 순수 단위 테스트. */
class DisasterEventAdvisoryTitleTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 7, 20, 12, 0);

    @Test
    @DisplayName("기타 안내 이벤트 제목은 '{지역명} 물놀이 안내'")
    void etcAdvisoryTitle_usesWaterLabel() {
        DisasterEvent e = DisasterEvent.createAdvisory("기타", "4373000000", "충청북도 옥천군", AT);

        assertThat(e.getEventTitle()).isEqualTo("충청북도 옥천군 물놀이 안내");
    }

    @Test
    @DisplayName("산불 안내 이벤트 제목은 기존 '{지역명} 산불예방안내' 유지 [회귀 보호]")
    void wildfireAdvisoryTitle_unchanged() {
        DisasterEvent e = DisasterEvent.createAdvisory("산불", "4373000000", "충청북도 옥천군", AT);

        assertThat(e.getEventTitle()).isEqualTo("충청북도 옥천군 산불예방안내");
    }
}
