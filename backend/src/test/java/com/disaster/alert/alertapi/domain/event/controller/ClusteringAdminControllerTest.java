package com.disaster.alert.alertapi.domain.event.controller;

import com.disaster.alert.alertapi.domain.event.judge.JudgeVersionCatalog;
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
 * 관리자 대시보드 조회 API 4개의 권한과 응답 내용을 실제 DB 와 함께 확인한다(계약: specs/008 contracts).
 * 테스트마다 트랜잭션이 롤백되므로 넣은 채점·검사·알림 행은 남지 않는다. 날짜는 실제 기록이 없을 과거(2001-03-0x),
 * 알림 id 는 실제 값과 겹치지 않는 큰 값(9_300_000_xxx)을 쓰고, 조회 범위를 그 날짜로만 좁혀 로컬 DB 의 실제 기록이 섞이지 않게 한다.
 *
 * <p>인증: 이 저장소에는 컨트롤러 테스트 전례가 없어 {@link WithMockUser} 로 ROLE_ADMIN / ROLE_USER 를 흉내 낸다.
 * 권한 판정은 컨트롤러의 {@code @PreAuthorize("hasRole('ADMIN')")} 가 하므로 JWT 쿠키까지 만들 필요가 없다(JWT 필터는 쿠키가 없으면 그냥 지나간다).
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class ClusteringAdminControllerTest {

    private static final String BASE = "/api/v1/admin/clustering";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper objectMapper;

    // 결정: 관리자 화면은 ADMIN 만 볼 수 있다. 일반 회원(USER)은 403 이고 ADMIN 은 200 이다. 프론트의 안내는 우회될 수 있어
    // 보안 경계가 아니므로(research R9) 백엔드가 최종으로 막아야 한다. Red 에서 ADMIN 은 뼈대도 200 이라 통과하지만,
    // USER 는 403 이 아니라 500 으로 실패한다 — @PreAuthorize 가 던진 AccessDeniedException 을 GlobalExceptionHandler 의
    // catch-all(Exception) 이 가로채 500 으로 바꾸기 때문이다(기존 /api/v1/admin/** 도 같은 문제). 고치는 일은 Green 몫이다.
    @Test
    @WithMockUser(roles = "USER")
    void 일반_회원은_403이다() throws Exception {
        // 실행·검증: 일반 회원이 네 엔드포인트 중 대표로 판사 버전 이력을 요청하면 거부된다
        mockMvc.perform(get(BASE + "/judge-versions")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 관리자는_200이다() throws Exception {
        mockMvc.perform(get(BASE + "/judge-versions")).andExpect(status().isOk());
    }

    // 결정(US1): 일일 채점은 요청 범위의 모든 날을 최신 날짜 먼저로 내고, 기록이 없는 날도 NO_RECORD 로 남긴다 — 건너뛰면
    // 채점이 꺼진 날의 구멍을 표에서 알 수 없다. 채점된 날은 비율 분자/분모, 판사 버전, "그날 기록에 복사된 당시" 일치율, 추정 비용을
    // 담는다. Red 에서는 뼈대가 days 를 빈 목록으로 돌려줘 첫 단언(날짜 3개)에서 실패한다.
    @Test
    @WithMockUser(roles = "ADMIN")
    void 일일_채점은_범위의_모든_날을_내고_채점된_날은_비율_판사_비용을_담는다() throws Exception {
        // 준비: 03-04 만 채점된 날. 맞게 묶음 3 / 과병합 1 / 과소병합 2 → 맞게 묶은 비율 3/(3+1), 빠짐없이 묶은 비율 3/(3+2)
        // 판사 v1 이지만 기록에 남은 일치율을 현재 목록 값(124/142, 77/80)과 다르게(100/110, 50/60) 넣어, 목록이 아니라 기록 값을 쓰는지 가른다
        // 토큰 1,000,000 / 100,000 → v1 단가(입력 2.5·출력 10 USD/1M)로 2.5 + 1.0 = 3.5
        score("2001-03-04", 8, 6, 3, 1, 2, "v1", 100, 110, 50, 60, 1_000_000, 100_000);

        // 실행: 03-02 ~ 03-04(3일) 범위를 ADMIN 으로 요청한다. 03-02·03-03 은 기록이 없다
        JsonNode data = getData("/daily-scores?from=2001-03-02&to=2001-03-04");

        // 검증: 범위의 모든 날 3개가 최신 날짜 먼저로 나온다
        JsonNode days = data.path("days");
        assertThat(days).hasSize(3);
        assertThat(days.get(0).path("date").asText()).isEqualTo("2001-03-04");
        assertThat(days.get(1).path("date").asText()).isEqualTo("2001-03-03");
        assertThat(days.get(2).path("date").asText()).isEqualTo("2001-03-02");
        // 검증: 기록 없는 날은 NO_RECORD (0% 점수 같은 값으로 지어내지 않는다)
        assertThat(days.get(1).path("status").asText()).isEqualTo("NO_RECORD");
        assertThat(days.get(2).path("status").asText()).isEqualTo("NO_RECORD");

        // 검증: 채점된 날은 SCORED 이고 비율이 분자/분모 그대로 나온다(백분율은 소수 첫째 자리: 3/4 = 75.0, 3/5 = 60.0)
        JsonNode day = days.get(0);
        assertThat(day.path("status").asText()).isEqualTo("SCORED");
        assertThat(day.path("purity").path("numerator").asInt()).isEqualTo(3);
        assertThat(day.path("purity").path("denominator").asInt()).isEqualTo(4);
        assertThat(day.path("purity").path("percent").asDouble()).isEqualTo(75.0);
        assertThat(day.path("completeness").path("numerator").asInt()).isEqualTo(3);
        assertThat(day.path("completeness").path("denominator").asInt()).isEqualTo(5);
        assertThat(day.path("completeness").path("percent").asDouble()).isEqualTo(60.0);
        // 검증: 판사 버전과 모델(모델은 판사 버전 목록에서), 일치율은 목록이 아니라 그날 기록의 당시 값
        JsonNode judge = day.path("judge");
        assertThat(judge.path("version").asText()).isEqualTo("v1");
        assertThat(judge.path("model").asText()).isEqualTo(JudgeVersionCatalog.find("v1").orElseThrow().model());
        assertThat(judge.path("sameAgreement").path("numerator").asInt()).isEqualTo(100);
        assertThat(judge.path("sameAgreement").path("denominator").asInt()).isEqualTo(110);
        assertThat(judge.path("diffAgreement").path("numerator").asInt()).isEqualTo(50);
        assertThat(judge.path("diffAgreement").path("denominator").asInt()).isEqualTo(60);
        // 검증: 추정 비용 = 토큰 × 그 버전 단가 = 3.5 USD
        assertThat(day.path("estimatedCostUsd").asDouble()).isEqualTo(3.5);
    }

    // 결정(US2): 불일치 쌍은 "조회 범위 날짜"의 OVER_MERGED·MISSED 쌍만 낸다. 맞춘 쌍(disagreement 가 null)과 범위 밖 날짜의 쌍이
    // 섞이면 관리자가 볼 필요 없는 쌍까지 페이지를 채운다. 쌍마다 두 알림의 원문·발송 시각·유형과 채점 당시 이벤트 번호를 담고,
    // 알림이 지워졌으면 id 는 두고 message 만 null 로 내려 화면이 "원문 없음"을 보이게 한다. Red 에서는 뼈대가 0건을 돌려줘 totalElements 에서 실패한다.
    @Test
    @WithMockUser(roles = "ADMIN")
    void 불일치_쌍은_범위의_과병합_과소병합만_내고_원문과_당시_이벤트를_담는다() throws Exception {
        // 준비: 알림 두 건(원문 있음). 알림 9_300_000_099 는 일부러 넣지 않는다(지워진 알림 상황)
        alert(9_300_000_001L, "원문 A", "2001-03-01 22:30:00", "수도");
        alert(9_300_000_002L, "원문 B", "2001-03-04 23:00:00", "수도");
        // 준비: 03-04 와 03-05 채점 기록(쌍이 외래 키로 기록을 참조한다). 조회는 03-04 만 한다
        score("2001-03-04", 3, 3, 0, 0, 0, "v1", 1, 1, 1, 1, 0, 0);
        score("2001-03-05", 3, 3, 0, 0, 0, "v1", 1, 1, 1, 1, 0, 0);
        // 준비: ① 과병합(시스템은 같은 이벤트 11, 판사 DIFF) ② 과소병합(시스템은 다른 이벤트 11·12, 판사 SAME, 한쪽 알림은 없음)
        // ③ 맞춘 쌍(disagreement null)은 제외 대상 ④ 03-05 의 과병합은 범위 밖이라 제외 대상
        pair("2001-03-04", 9_300_000_001L, 9_300_000_002L, "DIFF", true, 9_300_000_011L, 9_300_000_011L, "OVER_MERGED");
        pair("2001-03-04", 9_300_000_001L, 9_300_000_099L, "SAME", false, 9_300_000_011L, 9_300_000_012L, "MISSED");
        pair("2001-03-04", 9_300_000_002L, 9_300_000_099L, "SAME", true, 9_300_000_011L, 9_300_000_011L, null);
        pair("2001-03-05", 9_300_000_001L, 9_300_000_002L, "DIFF", true, 9_300_000_011L, 9_300_000_011L, "OVER_MERGED");

        // 실행: 03-04 하루만, kind 없이 요청한다
        JsonNode data = getData("/disagreements?from=2001-03-04&to=2001-03-04");

        // 검증: 범위의 불일치 2건뿐이다(③ 맞춘 쌍과 ④ 범위 밖 쌍이 섞이면 4건이 된다). 정렬은 쌍 id 오름차순이라 ① 다음 ②
        assertThat(data.path("totalElements").asLong()).isEqualTo(2);
        JsonNode pairs = data.path("pairs");
        assertThat(pairs).hasSize(2);
        // 검증: ① 종류·판사 판정·두 알림의 원문/발송 시각/유형·당시 이벤트 번호
        JsonNode first = pairs.get(0);
        assertThat(first.path("date").asText()).isEqualTo("2001-03-04");
        assertThat(first.path("kind").asText()).isEqualTo("OVER_MERGED");
        assertThat(first.path("judgeVerdict").asText()).isEqualTo("DIFF");
        assertThat(first.path("alertA").path("id").asLong()).isEqualTo(9_300_000_001L);
        assertThat(first.path("alertA").path("message").asText()).isEqualTo("원문 A");
        assertThat(first.path("alertA").path("sentAt").asText()).isEqualTo("2001-03-01T22:30:00");
        assertThat(first.path("alertA").path("type").asText()).isEqualTo("수도");
        assertThat(first.path("alertB").path("message").asText()).isEqualTo("원문 B");
        assertThat(first.path("eventA").asLong()).isEqualTo(9_300_000_011L);
        assertThat(first.path("eventB").asLong()).isEqualTo(9_300_000_011L);
        // 검증: ② 없는 알림은 id 는 그대로, message 는 null(화면이 "원문 없음"을 보인다). 과소병합이라 이벤트 번호가 서로 다르다
        JsonNode second = pairs.get(1);
        assertThat(second.path("kind").asText()).isEqualTo("MISSED");
        assertThat(second.path("alertB").path("id").asLong()).isEqualTo(9_300_000_099L);
        assertThat(isAbsentOrNull(second.path("alertB").path("message"))).isTrue();
        assertThat(second.path("eventA").asLong()).isNotEqualTo(second.path("eventB").asLong());
    }

    // 결정(US2): kind 필터를 주면 그 종류만 낸다. 과병합과 과소병합은 원인과 고칠 곳이 달라 따로 볼 수 있어야 한다.
    // Red 에서는 뼈대가 0건을 돌려줘 totalElements 에서 실패한다.
    @Test
    @WithMockUser(roles = "ADMIN")
    void kind_필터를_주면_그_종류의_쌍만_낸다() throws Exception {
        // 준비: 03-04 에 과병합 1건 + 과소병합 1건
        score("2001-03-04", 2, 2, 0, 0, 0, "v1", 1, 1, 1, 1, 0, 0);
        pair("2001-03-04", 9_300_000_001L, 9_300_000_002L, "DIFF", true, 9_300_000_011L, 9_300_000_011L, "OVER_MERGED");
        pair("2001-03-04", 9_300_000_003L, 9_300_000_004L, "SAME", false, 9_300_000_012L, 9_300_000_013L, "MISSED");

        // 실행: MISSED 만 요청한다
        JsonNode data = getData("/disagreements?from=2001-03-04&to=2001-03-04&kind=MISSED");

        // 검증: 과소병합 1건뿐이다(필터가 무시되면 2건)
        assertThat(data.path("totalElements").asLong()).isEqualTo(1);
        assertThat(data.path("pairs")).hasSize(1);
        assertThat(data.path("pairs").get(0).path("kind").asText()).isEqualTo("MISSED");
    }

    // 결정(US3): 구조 검사는 범위의 모든 날을 내고, 날짜마다 "사건 알림 수·미배정·임베딩 없음" 세 숫자를 담는다. 미배정이나
    // 임베딩 없음이 하나라도 있으면 attention 이 true — 관리자가 숫자를 일일이 읽지 않고도 문제 있는 날을 찾게 하려는 표시다.
    // Red 에서는 뼈대가 days 를 빈 목록으로 돌려줘 날짜 4개 단언에서 실패한다.
    @Test
    @WithMockUser(roles = "ADMIN")
    void 구조_검사는_날짜별_세_숫자를_내고_미배정_또는_임베딩_없음이_있으면_attention이다() throws Exception {
        // 준비: 03-04 정상(0/0), 03-03 미배정 2, 03-02 기록 없음, 03-01 임베딩 없음 1(미배정 0) — attention 의 두 조건을 각각 대표한다
        check("2001-03-04", 8, 0, 0);
        check("2001-03-03", 5, 2, 0);
        check("2001-03-01", 4, 0, 1);

        // 실행: 03-01 ~ 03-04(4일)
        JsonNode data = getData("/daily-checks?from=2001-03-01&to=2001-03-04");

        // 검증: 4일이 최신 먼저로 나온다
        JsonNode days = data.path("days");
        assertThat(days).hasSize(4);
        // 검증: 03-04 는 세 숫자가 그대로이고 문제 없음
        assertThat(days.get(0).path("recorded").asBoolean()).isTrue();
        assertThat(days.get(0).path("incidentCount").asInt()).isEqualTo(8);
        assertThat(days.get(0).path("unassignedCount").asInt()).isZero();
        assertThat(days.get(0).path("noEmbeddingCount").asInt()).isZero();
        assertThat(days.get(0).path("attention").asBoolean()).isFalse();
        // 검증: 03-03 은 미배정 2 → attention
        assertThat(days.get(1).path("unassignedCount").asInt()).isEqualTo(2);
        assertThat(days.get(1).path("attention").asBoolean()).isTrue();
        // 검증: 03-02 는 검사 기록 없음(recorded=false) — 0 건으로 지어내지 않는다
        assertThat(days.get(2).path("recorded").asBoolean()).isFalse();
        // 검증: 03-01 은 미배정 0 이지만 임베딩 없음 1 → attention
        assertThat(days.get(3).path("noEmbeddingCount").asInt()).isEqualTo(1);
        assertThat(days.get(3).path("attention").asBoolean()).isTrue();
    }

    // 결정(US4, specs/014 로 갱신): 판사 버전 이력은 버전 목록(JudgeVersionCatalog)의 모델을 최신 버전 먼저로 내고, 일치율은 그 버전의
    // 가장 늦은 측정 기록에서 센다(합계만 있는 측정이면 그 합계, recomputable=false). 버전마다 채점 기록에서 구한
    // 첫날·마지막 날·채점 일수를 붙인다. 채점 기록이 없는 버전은 날짜 null, 일수 0 이다(관리자가 "아직 이 버전으로 채점한 날 없음"을 알게).
    // Red 에서는 뼈대가 버전을 빈 목록으로 돌려줘 첫 단언에서 실패한다.
    @Test
    @WithMockUser(roles = "ADMIN")
    void 판사_버전_이력은_목록_값과_채점_기록의_기간을_내고_기록_없는_버전은_비어_있다() throws Exception {
        // 준비: 로컬 DB 에 실제 채점 기록(v1·v2)이 있으면 첫날·마지막 날이 섞이므로, 이 트랜잭션 안에서 기록을 모두 지운다(테스트 끝에 롤백)
        jdbc.update("DELETE FROM clustering_daily_score_pair");
        jdbc.update("DELETE FROM clustering_daily_score");
        // 준비: v1 로 03-02 와 03-04 두 날을 채점. v2 는 기록 없음
        score("2001-03-02", 1, 1, 1, 0, 0, "v1", 1, 1, 1, 1, 0, 0);
        score("2001-03-04", 1, 1, 1, 0, 0, "v1", 1, 1, 1, 1, 0, 0);
        // 준비: 버전마다 가장 늦은 측정으로 합계만 있는 측정을 넣는다 — 로컬 DB 라벨 유무와 상관없이 값이 정해지게(쌍별 판정 계산은
        // JudgeAgreementServiceTest 가 본다)
        measured("v2", 21, 22, 23, 24);
        measured("v1", 31, 32, 33, 34);

        // 실행
        JsonNode data = getData("/judge-versions");

        // 검증: 목록의 모든 버전이 최신 먼저 순서로 나오고(v2 → v1), 모델은 목록 값이다
        JsonNode versions = data.path("versions");
        assertThat(versions).hasSize(JudgeVersionCatalog.all().size());
        for (int i = 0; i < JudgeVersionCatalog.all().size(); i++) {
            JudgeVersionCatalog.Entry entry = JudgeVersionCatalog.all().get(i);
            assertThat(versions.get(i).path("version").asText()).isEqualTo(entry.version());
            assertThat(versions.get(i).path("model").asText()).isEqualTo(entry.model());
        }
        // 검증: 일치율·측정일은 그 버전의 가장 늦은 측정 값이고, 합계만 있는 측정이라 다시 계산되지 않는다고 표시한다
        JsonNode v2Agreement = versionNode(versions, "v2");
        assertThat(v2Agreement.path("sameAgreement").path("numerator").asInt()).isEqualTo(21);
        assertThat(v2Agreement.path("sameAgreement").path("denominator").asInt()).isEqualTo(22);
        assertThat(v2Agreement.path("diffAgreement").path("numerator").asInt()).isEqualTo(23);
        assertThat(v2Agreement.path("diffAgreement").path("denominator").asInt()).isEqualTo(24);
        assertThat(v2Agreement.path("measuredOn").asText()).isEqualTo("2099-01-01");
        assertThat(v2Agreement.path("recomputable").asBoolean()).isFalse();
        assertThat(versionNode(versions, "v1").path("sameAgreement").path("numerator").asInt()).isEqualTo(31);
        // 검증: v1 은 첫날 03-02, 마지막 날 03-04, 채점 2일(03-03 은 기록이 없어 세지 않는다)
        JsonNode v1 = versionNode(versions, "v1");
        assertThat(v1.path("firstScoredDate").asText()).isEqualTo("2001-03-02");
        assertThat(v1.path("lastScoredDate").asText()).isEqualTo("2001-03-04");
        assertThat(v1.path("scoredDays").asInt()).isEqualTo(2);
        // 검증: v2 는 채점 기록이 없어 날짜가 null, 일수 0
        JsonNode v2 = versionNode(versions, "v2");
        assertThat(isAbsentOrNull(v2.path("firstScoredDate"))).isTrue();
        assertThat(isAbsentOrNull(v2.path("lastScoredDate"))).isTrue();
        assertThat(v2.path("scoredDays").asInt()).isZero();
    }

    /** 그 버전의 가장 늦은 측정이 되도록 먼 미래 시각으로 합계만 있는 측정을 넣는다(테스트 끝에 롤백). */
    private void measured(String version, int sameNum, int sameDen, int diffNum, int diffDen) {
        jdbc.update("INSERT INTO judge_agreement_run(judge_version, model, dataset, origin, finished_at, prompt_tokens,"
                + " completion_tokens, estimated_usd, has_pair_verdicts, legacy_same_num, legacy_same_den, legacy_diff_num,"
                + " legacy_diff_den) VALUES (?, 'test-model', 'admin-test', 'LEGACY_IMPORT', '2099-01-01 00:00:00',"
                + " 0, 0, 0, false, ?, ?, ?, ?)", version, sameNum, sameDen, diffNum, diffDen);
    }

    /** ADMIN 으로 GET 해 200 을 확인하고, ApiResponse 의 data 부분만 JSON 으로 꺼낸다. */
    private JsonNode getData(String pathAndQuery) throws Exception {
        String body = mockMvc.perform(get(BASE + pathAndQuery))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private static JsonNode versionNode(JsonNode versions, String version) {
        for (JsonNode node : versions) {
            if (version.equals(node.path("version").asText())) {
                return node;
            }
        }
        throw new AssertionError("버전 " + version + " 이 응답에 없다");
    }

    // 키가 없거나 null 이면 "값 없음" — 계약은 null 이지만 NON_NULL 직렬화로 키가 빠져도 같은 뜻이다
    private static boolean isAbsentOrNull(JsonNode node) {
        return node.isMissingNode() || node.isNull();
    }

    /** 일일 채점 행 하나. 일치율 4개와 토큰은 호출한 테스트가 값을 정한다. */
    private void score(String date, int incident, int pairCount, int correctly, int over, int missed,
                       String version, int sameNum, int sameDen, int diffNum, int diffDen,
                       long promptTokens, long completionTokens) {
        jdbc.update("INSERT INTO clustering_daily_score(score_date, incident_count, pair_count, correctly_merged, over_merged, "
                        + "missed, unsure_count, format_failure_count, judge_version, agreement_same_num, agreement_same_den, "
                        + "agreement_diff_num, agreement_diff_den, prompt_tokens, completion_tokens, scored_at) "
                        + "VALUES (CAST(? AS date), ?, ?, ?, ?, ?, 0, 0, ?, ?, ?, ?, ?, ?, ?, now())",
                date, incident, pairCount, correctly, over, missed, version, sameNum, sameDen, diffNum, diffDen,
                promptTokens, completionTokens);
    }

    /** 판정한 쌍 하나. alert_a &lt; alert_b 제약이 있어 작은 id 를 앞에 넣는다. 이벤트 id 에는 외래 키가 없다. */
    private void pair(String date, long alertA, long alertB, String verdict, boolean systemSame,
                      Long eventA, Long eventB, String disagreement) {
        jdbc.update("INSERT INTO clustering_daily_score_pair(score_date, alert_a, alert_b, judge_verdict, system_same_event, "
                        + "event_a, event_b, disagreement) VALUES (CAST(? AS date), ?, ?, ?, ?, ?, ?, ?)",
                date, alertA, alertB, verdict, systemSame, eventA, eventB, disagreement);
    }

    private void check(String date, int incident, int unassigned, int noEmbedding) {
        jdbc.update("INSERT INTO clustering_daily_check(check_date, incident_count, unassigned_count, no_embedding_count, "
                        + "checked_at) VALUES (CAST(? AS date), ?, ?, ?, now())",
                date, incident, unassigned, noEmbedding);
    }

    /** 알림 하나. created_at 이 발송 시각이다. sn 은 외부 고유번호(UNIQUE)라 id 와 같은 큰 값을 쓴다. */
    private void alert(long id, String message, String createdAt, String type) {
        jdbc.update("INSERT INTO disaster_alert(disaster_alert_id, sn, message, created_at, disaster_type, alert_kind) "
                        + "VALUES (?, ?, ?, CAST(? AS timestamp), ?, 'INCIDENT')",
                id, id, message, createdAt, type);
    }
}
