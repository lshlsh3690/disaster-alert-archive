package com.disaster.alert.alertapi.domain.event.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MissingPersonIdentity} 순수 정적 유틸리티 단위 테스트.
 * 외부 의존성이 없으므로 Spring 컨텍스트 없이 순수 JUnit으로 검증한다.
 */
@DisplayName("MissingPersonIdentity")
class MissingPersonIdentityTest {

    @Test
    @DisplayName("정형 실종 문자에서 이름/나이/키를 모두 추출하고 isPerson은 true다")
    void extractsNameAgeHeightFromTypicalMessage() {
        String message = "[○○경찰서] 실종자를 찾습니다. 홍길동씨(남,60세), 실종 당시 신장 175cm, 회색 자켓 착용.";

        assertThat(MissingPersonIdentity.name(message)).isEqualTo("홍길동");
        assertThat(MissingPersonIdentity.age(message)).isEqualTo("60");
        assertThat(MissingPersonIdentity.height(message)).isEqualTo("175");
        assertThat(MissingPersonIdentity.isPerson(message)).isTrue();
    }

    @Test
    @DisplayName("4글자(복성 등) 이름도 추출한다")
    void extractsFourSyllableName() {
        String message = "남궁민수씨(남,45세) 실종";

        assertThat(MissingPersonIdentity.name(message)).isEqualTo("남궁민수");
    }

    @Test
    @DisplayName("키 정보가 없으면 height는 null이지만, 이름+나이만으로 isPerson은 true다")
    void isPersonTrueWithoutHeight() {
        String message = "실종자 김철수씨(남,30세) 목격 시 신고 바랍니다.";

        assertThat(MissingPersonIdentity.name(message)).isEqualTo("김철수");
        assertThat(MissingPersonIdentity.age(message)).isEqualTo("30");
        assertThat(MissingPersonIdentity.height(message)).isNull();
        assertThat(MissingPersonIdentity.isPerson(message)).isTrue();
    }

    @Test
    @DisplayName("이름 패턴(OOO씨)이 없으면 나이가 있어도 isPerson은 false다")
    void isPersonFalseWithoutName() {
        String message = "실종자 남성(60세) 목격 시 신고 바랍니다.";

        assertThat(MissingPersonIdentity.name(message)).isNull();
        assertThat(MissingPersonIdentity.age(message)).isEqualTo("60");
        assertThat(MissingPersonIdentity.isPerson(message)).isFalse();
    }

    @Test
    @DisplayName("나이 패턴(N세)이 없으면 이름이 있어도 isPerson은 false다")
    void isPersonFalseWithoutAge() {
        String message = "실종자 홍길동씨를 찾습니다. 목격 시 신고 바랍니다.";

        assertThat(MissingPersonIdentity.name(message)).isEqualTo("홍길동");
        assertThat(MissingPersonIdentity.age(message)).isNull();
        assertThat(MissingPersonIdentity.isPerson(message)).isFalse();
    }

    @Test
    @DisplayName("이름 접미사가 '씨'가 아니면(군/양 등) 이름이 매칭되지 않는다")
    void nameSuffixOtherThanSsiNotMatched() {
        String message = "실종자 홍길동군(남,10세)을 찾습니다.";

        assertThat(MissingPersonIdentity.name(message)).isNull();
        assertThat(MissingPersonIdentity.isPerson(message)).isFalse();
    }

    @Test
    @DisplayName("이름과 '씨' 사이에 공백이 있으면 매칭되지 않는다 (즉시 인접만 인식)")
    void nameRequiresNoWhitespaceBeforeSuffix() {
        String message = "실종자 홍길동 씨(남,60세)를 찾습니다.";

        assertThat(MissingPersonIdentity.name(message)).isNull();
    }

    @Test
    @DisplayName("나이/키 숫자와 단위 사이 공백은 허용된다")
    void ageAndHeightAllowWhitespaceBeforeUnit() {
        String message = "홍길동씨(남, 60 세), 신장 175 cm";

        assertThat(MissingPersonIdentity.age(message)).isEqualTo("60");
        assertThat(MissingPersonIdentity.height(message)).isEqualTo("175");
    }

    @Test
    @DisplayName("null 메시지는 모든 추출값과 isPerson이 안전하게 null/false를 반환한다")
    void nullMessageReturnsNullSafely() {
        assertThat(MissingPersonIdentity.name(null)).isNull();
        assertThat(MissingPersonIdentity.age(null)).isNull();
        assertThat(MissingPersonIdentity.height(null)).isNull();
        assertThat(MissingPersonIdentity.isPerson(null)).isFalse();
    }

    @DisplayName("N세 나이 패턴은 1~3자리 숫자를 인식한다")
    @ParameterizedTest(name = "\"{0}\" -> age {1}")
    @CsvSource({
            "5세, 5",
            "60세, 60",
            "100세, 100"
    })
    void ageRecognizesOneToThreeDigits(String message, String expected) {
        assertThat(MissingPersonIdentity.age(message)).isEqualTo(expected);
    }
}
