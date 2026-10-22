package com.disaster.alert.alertapi.domain.disasteralert.repository;

import com.disaster.alert.alertapi.domain.disasteralert.model.AlertKind;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 분류 백필의 대상 목록이 "미분류(alert_kind null)만, id 오름차순"인지 실제 DB 로 검증한다.
 * 이미 분류된 알림이 대상에 섞이면 백필을 다시 돌릴 때마다 같은 알림을 또 과금한다.
 * 데이터는 테스트 트랜잭션과 함께 롤백된다.
 */
@IntegrationTest
@Transactional
class UnclassifiedAlertIdsRepositoryTest {

    @Autowired
    private DisasterAlertRepository disasterAlertRepository;

    @Test
    @DisplayName("이미 분류된 알림은 빼고, 미분류 알림만 id 오름차순으로 돌려준다")
    void 미분류만_오름차순() {
        DisasterAlert unclassified1 = save(980_000_001L, null);
        DisasterAlert incident = save(980_000_002L, AlertKind.INCIDENT);
        DisasterAlert advisory = save(980_000_003L, AlertKind.ADVISORY);
        DisasterAlert unclassified2 = save(980_000_004L, null);

        List<Long> ids = disasterAlertRepository.findUnclassifiedIds();

        assertThat(ids).contains(unclassified1.getId(), unclassified2.getId())
                .doesNotContain(incident.getId(), advisory.getId())
                .isSorted();
    }

    private DisasterAlert save(long sn, AlertKind kind) {
        DisasterAlert alert = DisasterAlert.builder()
                .sn(sn)
                .message("테스트 문자 " + sn)
                .disasterType("기타")
                .createdAt(LocalDateTime.of(1999, 1, 1, 9, 0))
                .build();
        if (kind != null) {
            alert.classifyAs(kind);
        }
        return disasterAlertRepository.saveAndFlush(alert);
    }
}
