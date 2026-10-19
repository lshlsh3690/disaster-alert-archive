package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.AssignmentReader.PairFacts;
import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 테스트마다 트랜잭션이 롤백되므로 아래에서 넣는 알림·이벤트·법정동 행은 DB 에 남지 않는다.
 * id 는 실제 데이터와 겹치지 않도록 큰 값을 쓴다.
 */
@IntegrationTest
@Transactional
class AssignmentReaderTest {

    private static final long A1 = 9_000_000_001L; // 이벤트에 배정됨, 임베딩 [1,0,0...]
    private static final long A2 = 9_000_000_002L; // 이벤트에 배정 안 됨, 임베딩 [0,1,0...], A1 과 같은 시군구
    private static final long A3 = 9_000_000_003L; // 임베딩 없음, 다른 시군구, 유형이 다름

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    AssignmentReader reader;

    @BeforeEach
    void seed() {
        // 법정동: 앞 5자리 99001 을 공유하는 두 읍면동과, 다른 시군구 하나
        for (String code : new String[]{"9900110100", "9900110200", "9900210100"}) {
            jdbc.update("INSERT INTO legal_district(code, name, is_active, is_active_string) "
                    + "VALUES (?, ?, true, 'Y') ON CONFLICT (code) DO NOTHING", code, "테스트 " + code);
        }
        insertAlert(A1, "산불", "2026-10-01 10:00:00", vector(0));
        insertAlert(A2, "산불", "2026-10-01 12:50:00", vector(1)); // A1 과 2시간 50분 차
        insertAlert(A3, "호우", "2026-10-01 10:00:00", null);
        link(A1, "9900110100");
        link(A2, "9900110200"); // A1 과 앞 5자리(99001) 공유
        link(A3, "9900210100"); // 다른 시군구

        jdbc.update("INSERT INTO disaster_events(id, event_title, first_alert_at, last_alert_at, alert_count) "
                + "VALUES (9000000001, '평가 테스트 이벤트', '2026-10-01 10:00:00', '2026-10-01 10:00:00', 1)");
        jdbc.update("INSERT INTO event_alert_mapping(event_id, alert_id, sequence_no) VALUES (9000000001, ?, 1)", A1);
    }

    // 평가는 "두 알림 모두 이벤트에 들어 있는가" 로 미배정을 가른다. 이벤트에 없는 알림이 예외나 0 이 아니라
    // empty 로 나와야 호출부가 미배정 쌍을 제외할 수 있다.
    // Red 단계: 뼈대는 항상 Optional.empty() 라 배정된 A1 의 이벤트 id 기대에서 실패한다.
    @Test
    void 이벤트에_있으면_이벤트_id_없으면_empty() {
        // 검증: A1 은 시드한 이벤트에 배정, A2 는 매핑이 없어 empty
        assertThat(reader.findEventId(A1)).contains(9_000_000_001L);
        assertThat(reader.findEventId(A2)).isEmpty();
    }

    // 쌍의 사실 4개(유형, 같은 시군구, 간격, 본문 거리)를 DB 값으로 계산한다. 거리는 pgvector 의 <=> 로 DB 에서 구한다.
    // Red 단계: 뼈대는 빈 유형, sameSigungu=false, 간격 0, 거리 null 을 돌려줘 모든 기대에서 실패한다.
    @Test
    void 쌍의_사실_네_개를_계산한다() {
        // 실행: 같은 시군구·같은 유형·임베딩 둘 다 있는 쌍 / 다른 시군구·다른 유형·임베딩 한쪽 없는 쌍
        PairFacts close = reader.findFacts(A1, A2);
        PairFacts far = reader.findFacts(A1, A3);

        // 검증(A1,A2): 유형 하나, 시군구 공유, 2시간 50분 → 반올림 3시간, 서로 직교 벡터라 코사인 거리 1.0
        assertThat(close.type()).isEqualTo("산불");
        assertThat(close.sameSigungu()).isTrue();
        assertThat(close.gapHours()).isEqualTo(3);
        assertThat(close.embeddingDistance()).isCloseTo(1.0, within(1e-6));

        // 검증(A1,A3): 유형 "산불/호우", 시군구 불일치, 간격 0, 임베딩이 없으면 거리 null
        assertThat(far.type()).isEqualTo("산불/호우");
        assertThat(far.sameSigungu()).isFalse();
        assertThat(far.gapHours()).isZero();
        assertThat(far.embeddingDistance()).isNull();
    }

    private void insertAlert(long id, String type, String createdAt, String embedding) {
        jdbc.update("INSERT INTO disaster_alert(disaster_alert_id, sn, message, created_at, disaster_type, embedding) "
                        + "VALUES (?, ?, '테스트 본문', CAST(? AS timestamp), ?, CAST(? AS vector))",
                id, id, createdAt, type, embedding);
    }

    private void link(long alertId, String districtCode) {
        jdbc.update("INSERT INTO disaster_alert_region(disaster_alert_id, legal_district_code) VALUES (?, ?)",
                alertId, districtCode);
    }

    /** 1536 차원 중 hotIndex 번째만 1 인 pgvector 텍스트. */
    private static String vector(int hotIndex) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 1536; i++) {
            if (i > 0) sb.append(',');
            sb.append(i == hotIndex ? '1' : '0');
        }
        return sb.append(']').toString();
    }
}
