package com.disaster.alert.alertapi.domain.notification.repository;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.member.model.Member;
import com.disaster.alert.alertapi.domain.member.model.MemberRole;
import com.disaster.alert.alertapi.domain.member.repository.MemberRepository;
import com.disaster.alert.alertapi.domain.notification.model.UserNotificationLog;
import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * user_notification_log 대량 저장이 JDBC 배치로 나가는지 Hibernate 통계로 검증한다.
 *
 * <p>IDENTITY 에서는 INSERT 마다 PreparedStatement 가 1회씩 필요해 prepareStatementCount 가
 * INSERT 건수(1,200) 이상이 된다. SEQUENCE(allocationSize 500) + batch_size 500 이면
 * 시퀀스 조회 약 3회 + INSERT 배치 약 3회 수준이다. 임계값 100 은 그 사이에 충분한 여유를 둔 값이다.
 *
 * <p>테스트 메서드는 @Transactional(롤백)이라 시드 회원/알림/로그는 남지 않고,
 * 기존 user_notification_log 행은 건드리지 않는다.
 */
@IntegrationTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class UserNotificationLogBatchInsertTest {

    private static final int INSERT_COUNT = 1_200;

    @Autowired
    private UserNotificationLogRepository userNotificationLogRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private DisasterAlertRepository disasterAlertRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    @DisplayName("로그 1,200건 저장 시 PreparedStatement 수가 건수보다 훨씬 적고(JDBC 배치), id 가 유일하며 기존 최대 id 보다 크다")
    void 대량_저장은_배치로_나가고_id_는_충돌하지_않는다() {
        // given
        Member member = memberRepository.saveAndFlush(newMember());
        DisasterAlert alert = disasterAlertRepository.saveAndFlush(
                DisasterAlert.builder()
                        .sn(System.nanoTime()) // sn UNIQUE 회피
                        .message("테스트 - 배치 INSERT 검증용 재난문자")
                        .build());

        Number maxIdBefore = (Number) entityManager
                .createNativeQuery("SELECT COALESCE(MAX(id), 0) FROM user_notification_log")
                .getSingleResult();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        // when
        List<UserNotificationLog> logs = new ArrayList<>();
        for (int i = 0; i < INSERT_COUNT; i++) {
            logs.add(userNotificationLogRepository.save(UserNotificationLog.builder()
                    .memberId(member.getId())
                    .alertId(alert.getId())
                    .status("SENT")
                    .notificationType("PUSH")
                    .build()));
        }
        userNotificationLogRepository.flush();

        // then 1: 배치 여부
        long prepared = statistics.getPrepareStatementCount();
        assertThat(prepared)
                .as("PreparedStatement 수 (IDENTITY 면 INSERT 건수 이상)")
                .isLessThan(100);

        // then 2: id 유일성 / 기존 행과 비충돌
        List<Long> ids = logs.stream().map(UserNotificationLog::getId).toList();
        assertThat(ids).doesNotContainNull();
        assertThat(ids).doesNotHaveDuplicates();
        assertThat(ids).hasSize(INSERT_COUNT);
        assertThat(ids).allSatisfy(id -> assertThat(id).isGreaterThan(maxIdBefore.longValue()));
    }

    private Member newMember() {
        String unique = "batch-" + UUID.randomUUID();
        return Member.create(unique + "@test.com", "password", unique, MemberRole.USER);
    }
}
