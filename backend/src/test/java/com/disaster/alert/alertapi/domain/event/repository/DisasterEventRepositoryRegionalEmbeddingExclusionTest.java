package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 지역 한정 유형(산불·산사태·홍수·호우) 알림도 이제 임베딩을 저장한다. 그런데 다른 유형 문자의 임베딩 후보 검색이
 * 그 임베딩을 거리 계산에 쓰면, 예전엔 후보가 아니던 산불·호우 사건에 화재·교통 같은 문자가 합쳐질 수 있다.
 * 그래서 두 거리 쿼리(같은 시군구 / 인접 시군구)는 지역 한정 유형 알림의 임베딩을 거리 계산에서 빼야 한다.
 *
 * <p>실제 SQL(유형 제외 + 지역 조인 + 벡터 거리)이 핵심이라 DB 가 필요하다. 테스트마다 트랜잭션이 롤백되므로 넣은
 * 데이터는 남지 않는다. 로컬 DB 에 이미 있는 실제 이벤트가 결과에 섞일 수 있어 "결과가 비었다"가 아니라
 * "우리가 넣은 이벤트가 결과에 있는지/없는지"로 단언한다. 임베딩은 첫 차원만 1 인 벡터라 실제(조밀한) 임베딩과는
 * 거리가 멀고, 우리 알림과는 거리 0 이라 상위 3개에서 밀리지 않는다.
 */
@IntegrationTest
@Transactional
class DisasterEventRepositoryRegionalEmbeddingExclusionTest {

    private static final long EVENT_ID = 9_200_000_001L;
    private static final long ALERT_ID = 9_200_000_011L;
    // 서울 종로구(11110)와 중구(11140) — V37 시드에 양방향 인접 쌍으로 들어 있다.
    private static final String SIGUNGU_A = "11110";
    private static final String SIGUNGU_B = "11140";
    private static final LocalDateTime SINCE = LocalDateTime.of(2001, 3, 1, 0, 0);
    // application.yml 의 clustering.regional-types 키와 같은 목록 — 서비스가 넘겨 줄 값.
    private static final String[] REGIONAL_TYPES = {"산불", "산사태", "홍수", "호우"};

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    DisasterEventRepository repository;

    // 결정: 같은 시군구 후보 검색은 산불 알림의 임베딩을 거리 계산에서 뺀다. 그렇지 않으면 산불 사건 안의 알림이
    // 임베딩이 똑같은 화재 문자와 거리 0 으로 매칭돼 산불 사건에 화재 문자가 합쳐진다.
    // Red: 뼈대는 유형을 거르지 않아 산불 알림이 거리 0 으로 잡히고, 결과에 우리 이벤트가 들어 있어 단언에서 실패한다.
    @Test
    @DisplayName("같은 시군구 검색은 지역 한정 유형(산불) 알림의 임베딩을 거리 계산에서 뺀다")
    void 같은_시군구_검색은_산불_알림_임베딩을_뺀다() {
        // 준비: 종로구에 산불 알림 1건(임베딩 E)만 가진 이벤트 하나
        seedEventWithAlert("산불", SIGUNGU_A);

        // 실행: 같은 임베딩 E 로 종로구 후보를 찾는다(지역 한정 유형 제외 목록을 함께 넘긴다)
        List<Object[]> rows = repository.findTopCandidates(embedding(), new String[]{SIGUNGU_A}, SINCE, REGIONAL_TYPES);

        // 검증: 이 이벤트의 유일한 알림이 산불이라 거리 계산 대상이 없으므로 후보로 나오면 안 된다
        assertThat(eventIds(rows)).doesNotContain(EVENT_ID);
    }

    // 결정: 인접 시군구 후보 검색도 똑같이 산불 알림의 임베딩을 뺀다(사고성 문자가 이웃 시군구의 산불 사건에 붙는 경로 차단).
    // Red: 뼈대는 유형을 거르지 않아 이웃(종로구)의 산불 알림이 거리 0 으로 잡혀 단언에서 실패한다.
    @Test
    @DisplayName("인접 시군구 검색도 지역 한정 유형(산불) 알림의 임베딩을 거리 계산에서 뺀다")
    void 인접_시군구_검색은_산불_알림_임베딩을_뺀다() {
        // 준비: 종로구에 산불 알림 1건만 가진 이벤트. 새 알림은 이웃인 중구에서 들어온다고 본다
        seedEventWithAlert("산불", SIGUNGU_A);

        // 실행: 중구 기준으로 인접 후보를 찾는다 — region_adjacency(중구→종로구)로 종로구 알림이 대상이 된다
        List<Object[]> rows = repository.findTopAdjacentCandidates(embedding(), new String[]{SIGUNGU_B}, SINCE, REGIONAL_TYPES);

        // 검증: 이웃 알림이 산불뿐이라 거리 계산 대상이 없으므로 후보로 나오면 안 된다
        assertThat(eventIds(rows)).doesNotContain(EVENT_ID);
    }

    // 대조: 지역 한정 유형이 아닌 화재 알림은 예전 그대로 후보가 된다. 유형 제외가 "모든 알림 제외"로 잘못 구현되는 것을 막는다.
    // 뼈대에서도 통과한다(제외를 아직 안 하므로) — Green 에서 제외를 넣은 뒤에도 계속 통과해야 하는 짝이다.
    @Test
    @DisplayName("지역 한정 유형이 아닌 화재 알림은 그대로 후보가 된다")
    void 화재_알림은_그대로_후보가_된다() {
        // 준비: 종로구에 화재 알림 1건(임베딩 E)만 가진 이벤트
        seedEventWithAlert("화재", SIGUNGU_A);

        // 실행: 같은 임베딩 E 로 종로구 후보를 찾는다
        List<Object[]> rows = repository.findTopCandidates(embedding(), new String[]{SIGUNGU_A}, SINCE, REGIONAL_TYPES);

        // 검증: 화재는 제외 목록에 없으므로 거리 0 으로 후보에 들어온다
        assertThat(eventIds(rows)).contains(EVENT_ID);
    }

    /** 이벤트 하나와, 그 이벤트에 속하고 임베딩 E 를 가진 알림 1건(해당 시군구의 첫 법정동 코드 지역 행)을 넣는다. */
    private void seedEventWithAlert(String disasterType, String sigunguCode) {
        // 이벤트: 기간(SINCE 이후)·비광역(is_broadcast 기본 false)이라 findRecentEventIds 후보에 든다
        jdbc.update("INSERT INTO disaster_events(id, event_title, first_alert_at, last_alert_at, alert_count) "
                + "VALUES (?, '임베딩 제외 테스트 이벤트', '2001-03-04 10:00:00', '2001-03-04 10:00:00', 1)", EVENT_ID);
        jdbc.update("INSERT INTO disaster_alert(disaster_alert_id, sn, message, created_at, disaster_type, embedding, "
                        + "event_id, event_sequence_no) "
                        + "VALUES (?, ?, '테스트 본문', '2001-03-04 10:00:00', ?, CAST(? AS vector), ?, 1)",
                ALERT_ID, ALERT_ID, disasterType, embedding(), EVENT_ID);
        // 지역 행은 legal_district 외래 키가 있어 실제 법정동 코드를 골라 쓴다
        String districtCode = jdbc.queryForObject(
                "SELECT code FROM legal_district WHERE code LIKE ? ORDER BY code LIMIT 1", String.class, sigunguCode + "%");
        jdbc.update("INSERT INTO disaster_alert_region(disaster_alert_id, legal_district_code) VALUES (?, ?)",
                ALERT_ID, districtCode);
    }

    private static List<Long> eventIds(List<Object[]> rows) {
        return rows.stream().map(r -> ((Number) r[0]).longValue()).toList();
    }

    /** 첫 차원만 1 인 1536차원 벡터 문자열 — 같은 벡터끼리는 코사인 거리 0. */
    private static String embedding() {
        StringBuilder sb = new StringBuilder("[1");
        for (int i = 1; i < 1536; i++) {
            sb.append(",0");
        }
        return sb.append("]").toString();
    }
}
