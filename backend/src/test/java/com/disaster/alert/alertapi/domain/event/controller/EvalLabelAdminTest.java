package com.disaster.alert.alertapi.domain.event.controller;

import com.disaster.alert.alertapi.domain.event.evaluation.ClusteringCriteria;
import com.disaster.alert.alertapi.domain.member.model.Member;
import com.disaster.alert.alertapi.domain.member.model.MemberRole;
import com.disaster.alert.alertapi.domain.member.service.MemberDetails;
import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 평가 셋 사람 라벨을 관리자 화면에서 조회·수정하는 API 2개(GET/PUT /api/v1/admin/clustering/eval-labels)를 실제 DB 와 함께 확인한다.
 * {@link ClusteringAdminLabelingTest} 와 같은 방식으로 테스트마다 트랜잭션이 롤백되고, 알림 id 는 실제 값과 겹치지 않는
 * 큰 값(9_400_000_xxx), 평가 셋 이름은 실제 셋과 섞이지 않는 테스트 전용 이름을 쓴다.
 *
 * <p>인증: 수정자 회원 id 가 로그인 정보에서 오므로 {@code @WithMockUser} 대신 실제 서비스와 같은 {@link MemberDetails} 를 principal 로 넣는다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class EvalLabelAdminTest {

    private static final String URL = "/api/v1/admin/clustering/eval-labels";
    // 이 테스트 전용 평가 셋 이름 — 실제 평가 셋 라벨이 섞여 개수·목록이 달라지는 것을 막는다
    private static final String DATASET = "test-eval-9400";
    // 테스트가 쓰는 관리자 회원 id. 수정자(updated_by, changed_by)가 로그인 정보에서 오는지 확인하는 값이다(외래 키 없음)
    private static final long ADMIN_ID = 424_242L;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    EntityManager entityManager;

    // 결정: 라벨은 평가의 정답이라 관리자가 바꾸면 "무엇을 무엇으로, 누가, 왜" 가 남아야 한다. 라벨 행에는 새 라벨과 기준 시점
    // (ClusteringCriteria.VERSION_DATE)·수정자가 덮어써지고, 이력 테이블에는 이전·새 라벨·사유·수정자가 한 줄 쌓인다.
    // 이력이 없으면 정답이 왜 바뀌었는지 나중에 알 수 없고, 기준 시점이 옛 날짜로 남으면 새 기준으로 다시 본 라벨인지 구분이 안 된다.
    // Red 에서는 뼈대가 아무것도 저장하지 않아 첫 단언(라벨 기대 SAME, 실제 DIFF)에서 실패한다.
    @Test
    void 관리자가_라벨을_사유와_함께_고치면_라벨_기준시점_수정자가_바뀌고_이력이_한_줄_남는다() throws Exception {
        // 준비: 옛 기준 시점(2026-01-01)에 DIFF 로 매겨진 쌍. 수정 기록(updated_by)은 아직 없다
        alert(9_400_000_001L, "서울 강남구 화재 발생 안내");
        alert(9_400_000_002L, "서울 강남구 화재 진화 완료");
        long labelId = label(DATASET, 1, 9_400_000_001L, 9_400_000_002L, "DIFF", "2026-01-01");

        // 실행: 관리자가 DIFF → SAME 으로 사유와 함께 고친다
        update(admin(), labelId, "SAME", "같은 화재의 발생과 진화 안내라 같은 사건").andExpect(status().isOk());

        // 검증: 수정은 영속성 컨텍스트에만 있고 아직 DB 에 안 나갔을 수 있다 — jdbc 로 읽기 전에 내보낸다(안 하면 옛 값이 보인다)
        entityManager.flush();
        // 검증: 라벨은 SAME, 기준 시점은 옛 날짜가 아니라 서버 상수, 수정자는 로그인한 관리자
        Map<String, Object> saved = jdbc.queryForMap(
                "SELECT label, CAST(criteria_date AS varchar) AS criteria_date, updated_by "
                        + "FROM evaluation_label WHERE id = ?", labelId);
        assertThat(saved.get("label")).isEqualTo("SAME");
        assertThat(saved.get("criteria_date")).isEqualTo(ClusteringCriteria.VERSION_DATE.toString());
        assertThat(((Number) saved.get("updated_by")).longValue()).isEqualTo(ADMIN_ID);
        // 검증: 이력은 정확히 한 줄 — 이전(DIFF)·새(SAME) 라벨, 입력한 사유, 수정자
        List<Map<String, Object>> history = jdbc.queryForList(
                "SELECT old_label, new_label, reason, changed_by FROM evaluation_label_history WHERE label_id = ?", labelId);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("old_label")).isEqualTo("DIFF");
        assertThat(history.get(0).get("new_label")).isEqualTo("SAME");
        assertThat(history.get(0).get("reason")).isEqualTo("같은 화재의 발생과 진화 안내라 같은 사건");
        assertThat(((Number) history.get(0).get("changed_by")).longValue()).isEqualTo(ADMIN_ID);
    }

    // 결정: 같은 라벨로 저장하면 아무것도 바꾸지 않는다 — 화면에서 저장 버튼을 눌렀을 뿐 판단이 안 바뀐 경우까지 이력으로 남기면
    // "바뀐 적 없는 정답의 변경 기록"이 쌓여 진짜 수정 이력이 묻힌다. 수정자·시각도 그대로여야 마지막으로 고친 사람이 흐려지지 않는다.
    // Red 에서는 뼈대가 응답에 라벨을 채우지 않아(기대 DIFF, 실제 null) 응답 단언에서 실패한다.
    @Test
    void 같은_라벨로_저장하면_이력도_수정자도_남기지_않는다() throws Exception {
        // 준비: DIFF 로 매겨진 쌍(수정 기록 없음)
        alert(9_400_000_011L, "부산 해운대구 호우주의보");
        alert(9_400_000_012L, "부산 해운대구 호우경보");
        long labelId = label(DATASET, 2, 9_400_000_011L, 9_400_000_012L, "DIFF", "2026-01-01");

        // 실행: 같은 DIFF 로 사유와 함께 저장한다
        String body = update(admin(), labelId, "DIFF", "다시 봐도 다른 사건").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 검증: 응답은 바뀌지 않은 현재 라벨(DIFF)이다
        assertThat(objectMapper.readTree(body).path("data").path("label").asText()).isEqualTo("DIFF");
        entityManager.flush();
        // 검증: 이력이 한 줄도 없고, 수정자·기준 시점도 그대로(옛 날짜 유지, 수정자 없음)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evaluation_label_history WHERE label_id = ?",
                Integer.class, labelId)).isZero();
        Map<String, Object> saved = jdbc.queryForMap(
                "SELECT CAST(criteria_date AS varchar) AS criteria_date, updated_by FROM evaluation_label WHERE id = ?", labelId);
        assertThat(saved.get("criteria_date")).isEqualTo("2026-01-01");
        assertThat(saved.get("updated_by")).isNull();
    }

    // 결정: 목록은 관리자가 두 알림 원문을 나란히 보고 라벨이 맞는지 판단하는 화면의 재료다 — 쌍마다 id·라벨과 함께 두 알림의 message 가
    // 와야 하고, label 필터를 주면 그 라벨만 나온다(틀린 것 같은 DIFF 만 모아 보려는 용도). 요약 개수는 필터와 상관없이 셋 전체 기준이다.
    // Red 에서는 뼈대가 빈 목록을 줘 첫 단언(쌍 수 기대 2, 실제 0)에서 실패한다.
    @Test
    void 목록은_두_알림_원문과_함께_나오고_label_필터를_주면_그_라벨만_나온다() throws Exception {
        // 준비: 이 셋에 SAME 1쌍(q=1), DIFF 1쌍(q=2)
        alert(9_400_000_021L, "대전 서구 산불 발생");
        alert(9_400_000_022L, "대전 서구 산불 진화 중");
        alert(9_400_000_023L, "대전 유성구 폭염주의보");
        alert(9_400_000_024L, "대전 서구 교통사고 통제");
        label(DATASET, 1, 9_400_000_021L, 9_400_000_022L, "SAME", "2026-11-13");
        label(DATASET, 2, 9_400_000_023L, 9_400_000_024L, "DIFF", "2026-11-13");

        // 실행·검증: 필터 없이 조회하면 두 쌍이 문항 번호(q) 순으로 오고, 각 쌍에 두 알림 원문이 붙는다
        JsonNode all = getData(URL + "?dataset=" + DATASET);
        assertThat(all.path("totalElements").asLong()).isEqualTo(2);
        assertThat(all.path("pairs")).hasSize(2);
        JsonNode first = all.path("pairs").get(0);
        assertThat(first.path("q").asInt()).isEqualTo(1);
        assertThat(first.path("label").asText()).isEqualTo("SAME");
        assertThat(first.path("alertA").path("message").asText()).isEqualTo("대전 서구 산불 발생");
        assertThat(first.path("alertB").path("message").asText()).isEqualTo("대전 서구 산불 진화 중");
        // 검증: 라벨별 개수는 셋 전체(SAME 1, DIFF 1, UNSURE 0)
        assertThat(all.path("labelCounts").path("same").asLong()).isEqualTo(1);
        assertThat(all.path("labelCounts").path("diff").asLong()).isEqualTo(1);

        // 실행·검증: label=DIFF 를 주면 DIFF 쌍(q=2) 하나만 남는다(필터가 무시되면 2건). 개수 요약은 필터와 무관하게 그대로
        JsonNode onlyDiff = getData(URL + "?dataset=" + DATASET + "&label=DIFF");
        assertThat(onlyDiff.path("totalElements").asLong()).isEqualTo(1);
        assertThat(onlyDiff.path("pairs")).hasSize(1);
        assertThat(onlyDiff.path("pairs").get(0).path("q").asInt()).isEqualTo(2);
        assertThat(onlyDiff.path("labelCounts").path("same").asLong()).isEqualTo(1);
    }

    private static MemberDetails admin() {
        Member member = Member.builder().id(ADMIN_ID).email("admin-" + ADMIN_ID + "@test.local").password("x")
                .nickname("n" + ADMIN_ID).role(MemberRole.ADMIN).build();
        return new MemberDetails(member);
    }

    /** 라벨 수정 요청. 호출한 테스트가 상태 코드를 검증한다. */
    private ResultActions update(MemberDetails who, long labelId, String label, String reason) throws Exception {
        String body = "{\"label\":\"" + label + "\",\"reason\":\"" + reason + "\"}";
        return mockMvc.perform(put(URL + "/" + labelId).with(user(who))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** ADMIN 으로 GET 해 200 을 확인하고, ApiResponse 의 data 부분만 JSON 으로 꺼낸다. */
    private JsonNode getData(String pathAndQuery) throws Exception {
        String body = mockMvc.perform(get(pathAndQuery).with(user(admin())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    /** 알림 하나. evaluation_label 이 disaster_alert 를 외래 키로 참조하므로 먼저 넣는다. sn 은 UNIQUE 라 id 와 같은 큰 값을 쓴다. */
    private void alert(long id, String message) {
        jdbc.update("INSERT INTO disaster_alert(disaster_alert_id, sn, message, created_at, disaster_type, alert_kind) "
                + "VALUES (?, ?, ?, now(), '기타', 'INCIDENT')", id, id, message);
    }

    /** 평가 셋 라벨 한 줄(저장 API 를 거치지 않고 직접 넣는다). alert_a_id &lt; alert_b_id 라 작은 id 를 앞에 넣는다. 만든 줄의 id 를 돌려준다. */
    private long label(String dataset, int q, long alertA, long alertB, String label, String criteriaDate) {
        return jdbc.queryForObject("INSERT INTO evaluation_label(dataset, q, alert_a_id, alert_b_id, label, relation, criteria_date) "
                        + "VALUES (?, ?, ?, ?, ?, 'SAME_SIGUNGU', CAST(? AS date)) RETURNING id",
                Long.class, dataset, q, alertA, alertB, label, criteriaDate);
    }
}
