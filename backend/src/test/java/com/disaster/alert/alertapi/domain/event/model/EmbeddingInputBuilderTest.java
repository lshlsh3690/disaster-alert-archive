package com.disaster.alert.alertapi.domain.event.model;

import com.disaster.alert.alertapi.domain.event.model.EmbeddingInputBuilder.Options;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link EmbeddingInputBuilder} Red 테스트 — 임베딩 입력 문자열 조립(순수 로직).
 * 형식: "[유형][시군구명][시간대] 본문". 클래스가 아직 없으므로 컴파일 오류가 Red 사유다.
 */
class EmbeddingInputBuilderTest {

    private static final Options ALL_ON = new Options(true, true);
    private static final LocalDateTime NOON = LocalDateTime.of(2026, 10, 10, 12, 0);

    @Test
    @DisplayName("기본 형식은 [유형][시군구명][시간대] 본문이다")
    void defaultFormat() {
        String result = EmbeddingInputBuilder.build("산불", "강릉시", NOON, "산불이 발생했습니다", ALL_ON);

        assertThat(result).isEqualTo("[산불][강릉시][오후] 산불이 발생했습니다");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "0, 0, 새벽",
            "5, 59, 새벽",
            "6, 0, 오전",
            "11, 59, 오전",
            "12, 0, 오후",
            "17, 59, 오후",
            "18, 0, 밤",
            "23, 59, 밤"
    })
    @DisplayName("시간대 경계값: 새벽(0~5) / 오전(6~11) / 오후(12~17) / 밤(18~23)")
    void timeBucketBoundaries(int hour, int minute, String expectedBucket) {
        LocalDateTime sentAt = LocalDateTime.of(2026, 10, 10, hour, minute);

        String result = EmbeddingInputBuilder.build(null, null, sentAt, "본문", new Options(false, true));

        assertThat(result).isEqualTo("[" + expectedBucket + "] 본문");
    }

    @Test
    @DisplayName("둘 다 false면 본문 그대로(trim만) 반환한다")
    void bothFlagsOffReturnsBodyOnly() {
        String result = EmbeddingInputBuilder.build("산불", "강릉시", NOON, "본문입니다", new Options(false, false));

        assertThat(result).isEqualTo("본문입니다");
    }

    @Test
    @DisplayName("시간대만 켜면 [오후] 본문")
    void timeBucketOnly() {
        String result = EmbeddingInputBuilder.build("산불", "강릉시", NOON, "본문", new Options(false, true));

        assertThat(result).isEqualTo("[오후] 본문");
    }

    @Test
    @DisplayName("유형·시군구만 켜면 시간대 토큰이 없다")
    void typeAndRegionOnly() {
        String result = EmbeddingInputBuilder.build("산불", "강릉시", NOON, "본문", new Options(true, false));

        assertThat(result).isEqualTo("[산불][강릉시] 본문");
    }

    @Test
    @DisplayName("유형이 null/blank면 해당 토큰만 생략한다(빈 대괄호 금지)")
    void blankTypeOmitted() {
        assertThat(EmbeddingInputBuilder.build(null, "강릉시", NOON, "본문", ALL_ON))
                .isEqualTo("[강릉시][오후] 본문");
        assertThat(EmbeddingInputBuilder.build("   ", "강릉시", NOON, "본문", ALL_ON))
                .isEqualTo("[강릉시][오후] 본문");
    }

    @Test
    @DisplayName("시군구명이 null/blank면 해당 토큰만 생략한다(빈 대괄호 금지)")
    void blankRegionOmitted() {
        assertThat(EmbeddingInputBuilder.build("산불", null, NOON, "본문", ALL_ON))
                .isEqualTo("[산불][오후] 본문");
        assertThat(EmbeddingInputBuilder.build("산불", "", NOON, "본문", ALL_ON))
                .isEqualTo("[산불][오후] 본문");
    }

    @Test
    @DisplayName("sentAt이 null이면 시간대 토큰을 생략한다")
    void nullSentAtOmitsTimeBucket() {
        String result = EmbeddingInputBuilder.build("산불", "강릉시", null, "본문", ALL_ON);

        assertThat(result).isEqualTo("[산불][강릉시] 본문");
    }

    @Test
    @DisplayName("토큰이 전부 생략되면 접두 공백 없이 본문만 남는다")
    void allTokensOmittedYieldsBodyOnly() {
        String result = EmbeddingInputBuilder.build(null, null, null, "본문", ALL_ON);

        assertThat(result).isEqualTo("본문");
        assertThat(result).doesNotContain("[]");
    }

    @Test
    @DisplayName("본문 앞뒤 공백은 trim한다")
    void bodyIsTrimmed() {
        String result = EmbeddingInputBuilder.build("산불", "강릉시", NOON, "  \n본문\t ", ALL_ON);

        assertThat(result).isEqualTo("[산불][강릉시][오후] 본문");
    }

    @Test
    @DisplayName("message가 null이면 IllegalArgumentException")
    void nullMessageThrows() {
        assertThatThrownBy(() -> EmbeddingInputBuilder.build("산불", "강릉시", NOON, null, ALL_ON))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
