package com.disaster.alert.alertapi.domain.disasteralert.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.disaster.alert.alertapi.domain.disasteralert.model.AlertKind.ADVISORY;
import static com.disaster.alert.alertapi.domain.disasteralert.model.AlertKind.INCIDENT;
import static org.assertj.core.api.Assertions.assertThat;

class AlertKindScoreTest {

    @Test
    @DisplayName("사건을 비사건으로 분류한 것과 비사건을 사건으로 분류한 것을 각자의 분모로 따로 센다")
    void 두_오류를_따로_센다() {
        List<AlertKindScore.Item> items = List.of(
                new AlertKindScore.Item(1, INCIDENT, INCIDENT),
                new AlertKindScore.Item(2, INCIDENT, ADVISORY),   // 사건 → 비사건 (클러스터링에서 빠짐)
                new AlertKindScore.Item(3, INCIDENT, INCIDENT),
                new AlertKindScore.Item(4, ADVISORY, ADVISORY),
                new AlertKindScore.Item(5, ADVISORY, INCIDENT));  // 비사건 → 사건 (클러스터링에 들어감)

        AlertKindScore.Result r = AlertKindScore.score(items);

        assertThat(r.incidents()).isEqualTo(3);
        assertThat(r.incidentAsAdvisory()).containsExactly(2L);
        assertThat(r.advisories()).isEqualTo(2);
        assertThat(r.advisoryAsIncident()).containsExactly(5L);
    }

    @Test
    @DisplayName("분류 실패(null)는 어느 오류로도 세지 않고 분모에서도 빼서 따로 모은다")
    void 분류_실패는_따로() {
        List<AlertKindScore.Item> items = List.of(
                new AlertKindScore.Item(1, INCIDENT, null),
                new AlertKindScore.Item(2, ADVISORY, null),
                new AlertKindScore.Item(3, INCIDENT, INCIDENT));

        AlertKindScore.Result r = AlertKindScore.score(items);

        assertThat(r.failed()).containsExactly(1L, 2L);
        assertThat(r.incidents()).isEqualTo(1);
        assertThat(r.advisories()).isZero();
        assertThat(r.incidentAsAdvisory()).isEmpty();
        assertThat(r.advisoryAsIncident()).isEmpty();
    }
}
