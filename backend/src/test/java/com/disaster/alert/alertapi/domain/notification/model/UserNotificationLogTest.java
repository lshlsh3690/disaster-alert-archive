package com.disaster.alert.alertapi.domain.notification.model;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.SequenceGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IDENTITY 는 Hibernate 의 JDBC INSERT 배치를 꺼버리므로, 알림 팬아웃의 로그 INSERT 가
 * 건건이 DB 왕복이 된다. PK 전략이 시퀀스 기반(배치 가능)으로 유지되는지 못 박는다.
 */
class UserNotificationLogTest {

    private Field idField() throws NoSuchFieldException {
        return UserNotificationLog.class.getDeclaredField("id");
    }

    @Test
    @DisplayName("id 는 SEQUENCE 전략으로 생성된다 (IDENTITY 면 JDBC 배치가 꺼진다)")
    void id_전략은_SEQUENCE_이다() throws Exception {
        GeneratedValue generatedValue = idField().getAnnotation(GeneratedValue.class);

        assertThat(generatedValue).isNotNull();
        assertThat(generatedValue.strategy()).isEqualTo(GenerationType.SEQUENCE);
    }

    @Test
    @DisplayName("시퀀스는 기존 user_notification_log_id_seq 를 allocationSize 500 으로 쓴다")
    void 시퀀스_이름과_allocationSize() throws Exception {
        SequenceGenerator sequenceGenerator = idField().getAnnotation(SequenceGenerator.class);

        assertThat(sequenceGenerator).isNotNull();
        assertThat(sequenceGenerator.sequenceName()).isEqualTo("user_notification_log_id_seq");
        assertThat(sequenceGenerator.allocationSize()).isEqualTo(500);
    }

    @Test
    @DisplayName("@GeneratedValue 의 generator 이름은 @SequenceGenerator 의 name 과 일치한다")
    void generator_이름이_일치한다() throws Exception {
        GeneratedValue generatedValue = idField().getAnnotation(GeneratedValue.class);
        SequenceGenerator sequenceGenerator = idField().getAnnotation(SequenceGenerator.class);

        assertThat(generatedValue).isNotNull();
        assertThat(sequenceGenerator).isNotNull();
        assertThat(generatedValue.generator()).isNotBlank();
        assertThat(generatedValue.generator()).isEqualTo(sequenceGenerator.name());
    }
}
