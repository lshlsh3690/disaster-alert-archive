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
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 불일치 쌍 사람 라벨링 API 3개(라벨 저장·불일치 쌍 목록의 라벨 표시·어려운 쌍 판사 성적)를 실제 DB 와 함께 확인한다
 * (계약: specs/009 contracts/labeling-api.md). 008 {@link ClusteringAdminControllerTest} 와 같은 방식으로 테스트마다 트랜잭션이
 * 롤백되고, 날짜는 실제 기록이 없을 과거(2001-03-0x), 알림 id 는 실제 값과 겹치지 않는 큰 값(9_300_000_xxx)을 쓴다.
 *
 * <p>인증: 라벨 저장은 컨트롤러가 로그인 정보의 {@code id} 를 읽으므로(`@AuthenticationPrincipal(expression = "id")`),
 * {@code @WithMockUser} 가 만드는 일반 {@code User} 가 아니라 실제 서비스와 같은 {@link MemberDetails} 를 principal 로 넣는다.
 * 읽기 API 는 008 테스트처럼 권한만 보면 되지만 같은 도우미를 쓴다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class ClusteringAdminLabelingTest {

    private static final String BASE = "/api/v1/admin/clustering";
    // 라벨을 직접 넣는 테스트 데이터의 기준 시점(실제 저장 시에는 서버가 ClusteringCriteria.VERSION_DATE 를 붙인다)
    private static final String CRITERIA = "2026-11-13";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    EntityManager entityManager;

    // 결정(US1): 관리자가 불일치 쌍에 라벨을 매기면 쌍당 한 줄만 저장되고, 다시 매기면 새 줄이 아니라 그 줄을 덮어쓴다.
    // 줄이 늘면 같은 쌍에 라벨이 둘이 되어 "마지막 판단이 무엇인가"를 알 수 없고, 평가 도구가 같은 쌍을 두 번 센다.
    // 저장된 줄에는 라벨을 매길 때의 기준 시점(ClusteringCriteria.VERSION_DATE)이 함께 남아야 나중에 기준이 바뀐 뒤 라벨과 구분된다.
    // Red 에서는 뼈대가 아무것도 저장하지 않아 첫 단언(줄 수 1)에서 "기대 1, 실제 0" 으로 실패한다.
    @Test
    void 관리자가_라벨을_저장하고_다시_저장하면_한_줄만_남고_마지막_라벨과_기준_시점이_보인다() throws Exception {
        // 준비: 03-04 에 과병합으로 기록된 쌍(001, 002). 라벨은 이런 쌍에만 매길 수 있다
        score("2001-03-04", "v1");
        pair("2001-03-04", 9_300_000_001L, 9_300_000_002L, "DIFF", true, "OVER_MERGED");

        // 실행: 처음에는 DIFF, 두 번째에는 SAME 으로 저장한다. 요청의 id 순서를 일부러 뒤집어(002, 001) 보내도
        // 서버가 작은 id 를 a 로 맞춰 같은 쌍으로 취급해야 한다(DB 는 alert_a < alert_b 제약)
        putLabel(admin(), 9_300_000_002L, 9_300_000_001L, "DIFF").andExpect(status().isOk());
        putLabel(admin(), 9_300_000_001L, 9_300_000_002L, "SAME").andExpect(status().isOk());

        // 검증: 덮어쓰기는 영속성 컨텍스트에만 있고 아직 DB 에 안 나갔을 수 있다 — jdbc 로 읽기 전에 내보낸다(안 하면 옛 라벨이 보인다)
        entityManager.flush();
        // 검증: 두 번 저장했어도 이 쌍의 줄은 하나
        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM clustering_pair_label WHERE alert_a = ? AND alert_b = ?",
                Integer.class, 9_300_000_001L, 9_300_000_002L);
        assertThat(rows).isEqualTo(1);
        // 검증: 남은 줄은 마지막 라벨(SAME)이고 기준 시점은 서버 상수. 처음 매긴 시각·회원 id 가 함께 남는다
        Map<String, Object> saved = jdbc.queryForMap(
                "SELECT label, CAST(criteria_date AS varchar) AS criteria_date, created_by, updated_by "
                        + "FROM clustering_pair_label WHERE alert_a = ? AND alert_b = ?",
                9_300_000_001L, 9_300_000_002L);
        assertThat(saved.get("label")).isEqualTo("SAME");
        assertThat(saved.get("criteria_date")).isEqualTo(ClusteringCriteria.VERSION_DATE.toString());
        assertThat(((Number) saved.get("created_by")).longValue()).isEqualTo(ADMIN_ID);
        assertThat(((Number) saved.get("updated_by")).longValue()).isEqualTo(ADMIN_ID);
    }

    // 결정(US1): 라벨 저장은 ADMIN 만 할 수 있다. 라벨이 평가의 정답으로 쓰이므로 일반 회원이 값을 바꾸면 평가가 오염된다.
    // 403 이면 DB 에도 줄이 생기지 않아야 한다. Red 뼈대도 컨트롤러 단계에서 막히므로 이 테스트는 Red 에서도 통과한다(권한은
    // 008 에서 이미 구현된 @PreAuthorize 가 판정한다). 뼈대가 저장을 안 하니 "줄이 안 생김" 단언은 Green 에서 의미를 얻는다.
    @Test
    void 일반_회원이_저장하면_403이고_행이_생기지_않는다() throws Exception {
        // 준비: 관리자였다면 저장에 성공했을 쌍(과병합으로 기록됨)
        score("2001-03-04", "v1");
        pair("2001-03-04", 9_300_000_001L, 9_300_000_002L, "DIFF", true, "OVER_MERGED");

        // 실행·검증: 일반 회원(USER)의 저장은 거부된다
        putLabel(member(MemberRole.USER), 9_300_000_001L, 9_300_000_002L, "SAME").andExpect(status().isForbidden());

        // 검증: 거부됐으니 라벨 줄이 없다
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM clustering_pair_label WHERE alert_a = ? AND alert_b = ?",
                Integer.class, 9_300_000_001L, 9_300_000_002L)).isZero();
    }

    // 결정(US1): 불일치 쌍으로 기록된 적 없는 쌍에는 라벨을 매길 수 없다(404). 아무 id 나 받으면 존재하지도 않는 쌍의 라벨이
    // 평가의 정답으로 섞여 들어간다. 대표 사례는 "판사가 판정했지만 시스템과 일치한 쌍(disagreement 가 null)" — 라벨링 대상이
    // 아니다. Red 에서는 뼈대가 검증 없이 200 을 돌려줘 상태 코드 단언("기대 404, 실제 200")에서 실패한다.
    @Test
    void 불일치로_기록되지_않은_쌍에_저장하면_404이고_행이_생기지_않는다() throws Exception {
        // 준비: 판사가 같은 사건(SAME)이라 했고 시스템도 같은 이벤트에 넣은 쌍 — 맞춘 쌍이라 disagreement 가 null
        score("2001-03-04", "v1");
        pair("2001-03-04", 9_300_000_001L, 9_300_000_002L, "SAME", true, null);

        // 실행·검증: 이 쌍에 라벨을 저장하려 하면 CLUSTERING_PAIR_NOT_FOUND(404)
        putLabel(admin(), 9_300_000_001L, 9_300_000_002L, "DIFF").andExpect(status().isNotFound());

        // 검증: 거부됐으니 라벨 줄이 없다
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM clustering_pair_label WHERE alert_a = ? AND alert_b = ?",
                Integer.class, 9_300_000_001L, 9_300_000_002L)).isZero();
    }

    // 결정(US1): 불일치 쌍 목록은 쌍마다 사람 라벨·기준 시점·"누가 틀렸는가"(wrongSide)를 붙이고, unlabeledOnly=true 면
    // 라벨 있는 쌍을 뺀다 — 관리자가 아직 안 매긴 쌍만 이어서 보게 하려는 것이다. 사람 라벨이 정답이므로 라벨 DIFF + 시스템이 같은
    // 이벤트로 묶은 쌍은 시스템이 틀렸고(SYSTEM), 라벨 DIFF + 판사가 SAME 이라 한 쌍은 판사가 틀렸다(JUDGE).
    // Red 에서는 뼈대가 세 필드를 항상 null 로 내려 첫 쌍의 humanLabel 단언("기대 DIFF, 실제 없음")에서 실패한다.
    @Test
    void 불일치_쌍에_라벨과_틀린_쪽이_붙고_unlabeledOnly면_라벨된_쌍이_빠진다() throws Exception {
        // 준비: 03-04 에 불일치 3쌍. 정렬은 쌍 id 오름차순이라 아래 입력 순서 그대로 나온다
        score("2001-03-04", "v1");
        // ① 과병합(시스템 같은 이벤트, 판사 DIFF) — 사람이 DIFF 라 하면 시스템이 틀렸다
        pair("2001-03-04", 9_300_000_001L, 9_300_000_002L, "DIFF", true, "OVER_MERGED");
        // ② 과소병합(시스템 다른 이벤트, 판사 SAME) — 사람이 DIFF 라 하면 판사가 틀렸다
        pair("2001-03-04", 9_300_000_003L, 9_300_000_004L, "SAME", false, "MISSED");
        // ③ 라벨 없는 과병합 — unlabeledOnly 에서 남아야 하는 쌍
        pair("2001-03-04", 9_300_000_005L, 9_300_000_006L, "DIFF", true, "OVER_MERGED");
        label(9_300_000_001L, 9_300_000_002L, "DIFF");
        label(9_300_000_003L, 9_300_000_004L, "DIFF");

        // 실행: 라벨 여부와 상관없이 전부 조회
        JsonNode all = getData("/disagreements?from=2001-03-04&to=2001-03-04");

        // 검증: 3쌍 모두 나오고, 라벨 쌍에는 라벨·기준 시점·틀린 쪽이 붙는다
        assertThat(all.path("pairs")).hasSize(3);
        JsonNode first = all.path("pairs").get(0);
        assertThat(first.path("humanLabel").asText()).isEqualTo("DIFF");
        assertThat(first.path("criteriaDate").asText()).isEqualTo(CRITERIA);
        assertThat(first.path("wrongSide").asText()).isEqualTo("SYSTEM");
        assertThat(all.path("pairs").get(1).path("wrongSide").asText()).isEqualTo("JUDGE");
        // 검증: 라벨 없는 쌍은 세 필드 모두 값 없음(null 은 JSON 에서 빠질 수 있다)
        JsonNode unlabeled = all.path("pairs").get(2);
        assertThat(isAbsentOrNull(unlabeled.path("humanLabel"))).isTrue();
        assertThat(isAbsentOrNull(unlabeled.path("wrongSide"))).isTrue();

        // 실행·검증: unlabeledOnly=true 면 라벨된 ①②가 빠지고 ③ 하나만 남는다(필터가 무시되면 3건)
        JsonNode onlyUnlabeled = getData("/disagreements?from=2001-03-04&to=2001-03-04&unlabeledOnly=true");
        assertThat(onlyUnlabeled.path("totalElements").asLong()).isEqualTo(1);
        assertThat(onlyUnlabeled.path("pairs")).hasSize(1);
        assertThat(onlyUnlabeled.path("pairs").get(0).path("alertA").path("id").asLong()).isEqualTo(9_300_000_005L);
    }

    // 결정(US2): 어려운 쌍 성적은 "지금 불일치인 쌍 중 사람 라벨이 있는 것"을 판사 버전별로 센다. 판사가 맞은 수와 시스템이 맞은 수는
    // 분모가 같고(SAME/DIFF 라벨 쌍), 모르겠음(UNSURE)은 정답이 없으니 분모에서 빼고 개수만 따로 낸다. 라벨 없는 쌍은 아예 세지 않는다.
    // 버전은 쌍을 채점한 날의 판사 버전으로 나누고 최신 버전(v2)이 먼저 온다. Red 에서는 뼈대가 빈 결과를 줘 첫 단언
    // (labeledPairs 기대 5, 실제 0)에서 실패한다.
    @Test
    void 어려운_쌍_성적은_판사_버전별로_판사_맞음_시스템_맞음_모르겠음을_센다() throws Exception {
        // 준비: 로컬 DB 에 실제 라벨이 있으면 숫자가 섞이므로 이 트랜잭션 안에서 비운다(테스트 끝에 롤백)
        jdbc.update("DELETE FROM clustering_pair_label");
        // 준비: 03-04 는 판사 v1, 03-05 는 판사 v2 로 채점한 날
        score("2001-03-04", "v1");
        score("2001-03-05", "v2");
        // v1 ① 과병합(판사 DIFF·시스템 같은 이벤트), 라벨 DIFF → 판사 맞음·시스템 틀림
        pair("2001-03-04", 9_300_000_101L, 9_300_000_102L, "DIFF", true, "OVER_MERGED");
        label(9_300_000_101L, 9_300_000_102L, "DIFF");
        // v1 ② 과소병합(판사 SAME·시스템 분리), 라벨 DIFF → 판사 틀림·시스템 맞음
        pair("2001-03-04", 9_300_000_103L, 9_300_000_104L, "SAME", false, "MISSED");
        label(9_300_000_103L, 9_300_000_104L, "DIFF");
        // v2 ③④ 과소병합(판사 SAME·시스템 분리), 라벨 SAME → 판사 맞음·시스템 틀림 (두 쌍)
        pair("2001-03-05", 9_300_000_105L, 9_300_000_106L, "SAME", false, "MISSED");
        label(9_300_000_105L, 9_300_000_106L, "SAME");
        pair("2001-03-05", 9_300_000_107L, 9_300_000_108L, "SAME", false, "MISSED");
        label(9_300_000_107L, 9_300_000_108L, "SAME");
        // v2 ⑤ 모르겠음 — 분모에서 빠지고 unsureCount 로만 센다
        pair("2001-03-05", 9_300_000_109L, 9_300_000_110L, "DIFF", true, "OVER_MERGED");
        label(9_300_000_109L, 9_300_000_110L, "UNSURE");
        // v2 ⑥ 라벨 없는 불일치 쌍 — 어느 숫자에도 들어가면 안 된다
        pair("2001-03-05", 9_300_000_111L, 9_300_000_112L, "DIFF", true, "OVER_MERGED");

        // 실행
        JsonNode data = getData("/hard-pair-grades");

        // 검증: 라벨된 쌍은 ①~⑤ 5개(⑥ 제외)
        assertThat(data.path("labeledPairs").asInt()).isEqualTo(5);
        JsonNode versions = data.path("versions");
        assertThat(versions).hasSize(2);
        // 검증: 최신 버전 v2 가 먼저. 판사 맞음 2/2(③④), 시스템 맞음 0/2, 모르겠음 1(⑤는 분모에서 빠짐)
        assertThat(versions.get(0).path("version").asText()).isEqualTo("v2");
        assertThat(versions.get(0).path("judgeRight").path("numerator").asInt()).isEqualTo(2);
        assertThat(versions.get(0).path("judgeRight").path("denominator").asInt()).isEqualTo(2);
        assertThat(versions.get(0).path("systemRight").path("numerator").asInt()).isZero();
        assertThat(versions.get(0).path("systemRight").path("denominator").asInt()).isEqualTo(2);
        assertThat(versions.get(0).path("unsureCount").asInt()).isEqualTo(1);
        // 검증: v1 은 ①②가 서로 반대로 갈려 판사 1/2, 시스템 1/2(백분율 50.0), 모르겠음 0
        assertThat(versions.get(1).path("version").asText()).isEqualTo("v1");
        assertThat(versions.get(1).path("judgeRight").path("numerator").asInt()).isEqualTo(1);
        assertThat(versions.get(1).path("judgeRight").path("denominator").asInt()).isEqualTo(2);
        assertThat(versions.get(1).path("judgeRight").path("percent").asDouble()).isEqualTo(50.0);
        assertThat(versions.get(1).path("systemRight").path("numerator").asInt()).isEqualTo(1);
        assertThat(versions.get(1).path("systemRight").path("denominator").asInt()).isEqualTo(2);
        assertThat(versions.get(1).path("unsureCount").asInt()).isZero();
    }

    // 테스트가 쓰는 관리자 회원 id. 라벨 줄의 created_by/updated_by 가 로그인 정보에서 오는지 확인하는 값이다(외래 키 없음)
    private static final long ADMIN_ID = 424_242L;

    private static MemberDetails memberDetails(MemberRole role, long id) {
        Member member = Member.builder().id(id).email("admin-" + id + "@test.local").password("x")
                .nickname("n" + id).role(role).build();
        return new MemberDetails(member);
    }

    private static MemberDetails admin() {
        return memberDetails(MemberRole.ADMIN, ADMIN_ID);
    }

    private static MemberDetails member(MemberRole role) {
        return memberDetails(role, 424_243L);
    }

    /** 라벨 저장 요청. 호출한 테스트가 상태 코드를 검증한다. */
    private org.springframework.test.web.servlet.ResultActions putLabel(MemberDetails who, long alertA, long alertB,
                                                                        String label) throws Exception {
        String body = "{\"alertA\":" + alertA + ",\"alertB\":" + alertB + ",\"label\":\"" + label + "\"}";
        return mockMvc.perform(put(BASE + "/disagreement-labels").with(user(who))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** ADMIN 으로 GET 해 200 을 확인하고, ApiResponse 의 data 부분만 JSON 으로 꺼낸다. */
    private JsonNode getData(String pathAndQuery) throws Exception {
        String body = mockMvc.perform(get(BASE + pathAndQuery).with(user(admin())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    // 키가 없거나 null 이면 "값 없음" — 계약은 null 이지만 NON_NULL 직렬화로 키가 빠져도 같은 뜻이다
    private static boolean isAbsentOrNull(JsonNode node) {
        return node.isMissingNode() || node.isNull();
    }

    /** 일일 채점 행 하나. 쌍이 외래 키로 이 행을 참조한다. 숫자 열은 이 테스트에 상관없어 0, 판사 버전만 호출한 쪽이 정한다. */
    private void score(String date, String version) {
        jdbc.update("INSERT INTO clustering_daily_score(score_date, incident_count, pair_count, correctly_merged, over_merged, "
                        + "missed, unsure_count, format_failure_count, judge_version, agreement_same_num, agreement_same_den, "
                        + "agreement_diff_num, agreement_diff_den, prompt_tokens, completion_tokens, scored_at) "
                        + "VALUES (CAST(? AS date), 0, 0, 0, 0, 0, 0, 0, ?, 1, 1, 1, 1, 0, 0, now())",
                date, version);
    }

    /** 판정한 쌍 하나. alert_a &lt; alert_b 제약이 있어 작은 id 를 앞에 넣는다. 이벤트 id 는 이 테스트에 상관없어 비운다. */
    private void pair(String date, long alertA, long alertB, String verdict, boolean systemSame, String disagreement) {
        jdbc.update("INSERT INTO clustering_daily_score_pair(score_date, alert_a, alert_b, judge_verdict, system_same_event, "
                        + "event_a, event_b, disagreement) VALUES (CAST(? AS date), ?, ?, ?, ?, NULL, NULL, ?)",
                date, alertA, alertB, verdict, systemSame, disagreement);
    }

    /** 이미 매겨져 있는 사람 라벨 하나(저장 API 를 거치지 않고 직접 넣는다). */
    private void label(long alertA, long alertB, String label) {
        jdbc.update("INSERT INTO clustering_pair_label(alert_a, alert_b, label, criteria_date, created_by, created_at, "
                        + "updated_by, updated_at) VALUES (?, ?, ?, CAST(? AS date), ?, now(), ?, now())",
                alertA, alertB, label, CRITERIA, ADMIN_ID, ADMIN_ID);
    }
}
