package com.disaster.alert.alertapi.domain.event.controller;

import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 구조 검사 표의 한 날짜 행을 펼쳤을 때 쓰는 "그날의 문제 알림" 조회 API 를 실제 DB 와 함께 확인한다.
 * 테스트마다 트랜잭션이 롤백되므로 넣은 알림·이벤트는 남지 않는다. 날짜는 실제 알림이 없을 과거(2001-04-04),
 * id 는 다른 테스트(9_100_·9_300_ 대역)와 겹치지 않는 9_700_000_xxx 를 써서 로컬 DB 의 실제 알림이 섞이지 않게 한다.
 * 권한(ADMIN 만)은 컨트롤러 클래스의 {@code @PreAuthorize} 가 이미 판정하므로 여기서 다시 확인하지 않는다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class ClusteringDailyCheckAlertsTest {

    private static final String DAY = "2001-04-04";
    private static final long EVENT_ID = 9_700_000_001L;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper objectMapper;

    // 결정: 펼친 목록은 "구조 검사 숫자가 센 문제 알림"(사건 알림 중 미배정 또는 임베딩 없음)만 낸다. 숫자 3 을 눌렀는데 정상 알림이
    // 섞여 5건이 나오거나, 숫자에 안 잡히는 안내(비사건) 알림이 끼면 관리자가 숫자와 목록을 대조할 수 없다. 각 알림에는 어느 문제인지
    // (unassigned/noEmbedding) 와 지금 붙은 이벤트·합류 방식을 담아 원인을 바로 읽게 한다. 정렬은 발송 시각 오름차순.
    // Red 에서는 뼈대가 alerts 를 빈 목록으로 돌려줘 첫 단언(hasSize(2))에서 실패한다.
    @Test
    @WithMockUser(roles = "ADMIN")
    void 그날의_문제_사건_알림만_발송_시각_순으로_내고_문제_종류와_이벤트를_담는다() throws Exception {
        // 준비: 배정된 알림이 매달릴 이벤트 하나 (disaster_alert.event_id 가 이벤트를 외래 키로 참조한다)
        jdbc.update("INSERT INTO disaster_events(id, event_title, first_alert_at, last_alert_at, alert_count) "
                + "VALUES (?, '문제 알림 테스트 이벤트', '2001-04-04 09:00:00', '2001-04-04 11:00:00', 2)", EVENT_ID);
        // 준비: 임베딩 없음(11:00)을 미배정(10:00)보다 먼저 넣는다 — 정렬이 없으면 넣은 순서대로 나와 순서 단언이 깨진다
        insertAlert(9_700_000_103L, "INCIDENT", "2001-04-04 11:00:00", "산불", "임베딩 없는 알림", false, true, "REGIONAL_TYPE");
        insertAlert(9_700_000_102L, "INCIDENT", "2001-04-04 10:00:00", "화재", "미배정 알림", true, false, null);
        // 준비: 정상(이벤트 있음·임베딩 있음) 사건 알림 — 문제가 아니라 목록에서 빠져야 한다
        insertAlert(9_700_000_101L, "INCIDENT", "2001-04-04 09:00:00", "화재", "정상 알림", false, false, "EMBEDDING");
        // 준비: 비사건(ADVISORY) 미배정 알림 — 구조 검사는 INCIDENT 만 세므로 목록에도 없어야 한다
        insertAlert(9_700_000_104L, "ADVISORY", "2001-04-04 12:00:00", "폭염", "안내성 미배정 알림", true, false, null);

        // 실행: 그날의 문제 알림을 ADMIN 으로 요청한다
        String body = mockMvc.perform(get("/api/v1/admin/clustering/daily-checks/" + DAY + "/alerts"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");

        // 검증: 문제 사건 알림 2건뿐이다(정상이 섞이면 3건, 비사건이 섞이면 4건). 날짜는 요청한 날 그대로
        assertThat(data.path("date").asText()).isEqualTo(DAY);
        JsonNode alerts = data.path("alerts");
        assertThat(alerts).hasSize(2);

        // 검증: 첫째는 10:00 미배정 알림 — 이벤트가 없으니 eventId·mergeMethod 는 비어 있고 unassigned 만 true
        JsonNode unassigned = alerts.get(0);
        assertThat(unassigned.path("id").asLong()).isEqualTo(9_700_000_102L);
        assertThat(unassigned.path("sentAt").asText()).isEqualTo("2001-04-04T10:00:00");
        assertThat(unassigned.path("type").asText()).isEqualTo("화재");
        assertThat(unassigned.path("message").asText()).isEqualTo("미배정 알림");
        assertThat(unassigned.path("unassigned").asBoolean()).isTrue();
        assertThat(unassigned.path("noEmbedding").asBoolean()).isFalse();
        assertThat(isAbsentOrNull(unassigned.path("eventId"))).isTrue();
        assertThat(isAbsentOrNull(unassigned.path("mergeMethod"))).isTrue();

        // 검증: 둘째는 11:00 임베딩 없는 알림 — 이벤트에는 붙어 있어 unassigned 는 false, noEmbedding 만 true 이고 붙은 이벤트·방식이 나온다
        JsonNode noEmbedding = alerts.get(1);
        assertThat(noEmbedding.path("id").asLong()).isEqualTo(9_700_000_103L);
        assertThat(noEmbedding.path("unassigned").asBoolean()).isFalse();
        assertThat(noEmbedding.path("noEmbedding").asBoolean()).isTrue();
        assertThat(noEmbedding.path("eventId").asLong()).isEqualTo(EVENT_ID);
        assertThat(noEmbedding.path("mergeMethod").asText()).isEqualTo("REGIONAL_TYPE");
    }

    // 키가 없거나 null 이면 "값 없음" — NON_NULL 직렬화로 키가 빠져도 같은 뜻이다
    private static boolean isAbsentOrNull(JsonNode node) {
        return node.isMissingNode() || node.isNull();
    }

    /**
     * 알림 하나. unassigned=true 면 event_id 등 이벤트 열을 NULL 로 두고(미배정), false 면 위에서 만든 이벤트에 붙이고 합류 방식을 적는다.
     * noEmbedding=true 면 embedding NULL. sn 은 외부 고유번호(UNIQUE)라 id 와 같은 큰 값을 쓴다.
     */
    private void insertAlert(long id, String kind, String createdAt, String type, String message,
                             boolean unassigned, boolean noEmbedding, String mergeMethod) {
        jdbc.update("INSERT INTO disaster_alert(disaster_alert_id, sn, message, created_at, disaster_type, alert_kind, "
                        + "embedding, event_id, event_sequence_no, event_merge_method) "
                        + "VALUES (?, ?, ?, CAST(? AS timestamp), ?, ?, CAST(? AS vector), ?, ?, ?)",
                id, id, message, createdAt, type, kind,
                noEmbedding ? null : vector(),
                unassigned ? null : EVENT_ID,
                unassigned ? null : 1,
                unassigned ? null : mergeMethod);
    }

    /** 1536 차원 중 첫 칸만 1 인 pgvector 텍스트 - 값 자체는 상관없고 NULL 이 아니라는 점만 필요하다. */
    private static String vector() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 1536; i++) {
            if (i > 0) sb.append(',');
            sb.append(i == 0 ? '1' : '0');
        }
        return sb.append(']').toString();
    }
}
