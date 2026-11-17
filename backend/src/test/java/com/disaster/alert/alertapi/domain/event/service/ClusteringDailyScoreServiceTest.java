package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge.Agreement;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge.JudgeInput;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge.JudgeResult;
import com.disaster.alert.alertapi.domain.event.judge.JudgeVerdict;
import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyScore;
import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyScorePair;
import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyScorePair.Disagreement;
import com.disaster.alert.alertapi.domain.event.repository.ClusteringDailyScorePairRepository;
import com.disaster.alert.alertapi.domain.event.repository.ClusteringDailyScoreRepository;
import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 일일 채점의 두 결정(어떤 쌍을 판사에게 묻는가 / 판정을 어떻게 점수로 저장하는가)을 DB 와 함께 확인한다.
 * 판사는 {@link MockitoBean} 으로 바꿔 실제 OpenAI 를 부르지 않는다. 테스트마다 트랜잭션이 롤백되므로 넣은 알림·이벤트·
 * 법정동·이웃 행은 남지 않는다. 채점 날짜는 실제 알림이 없을 과거 날짜(2001-03-04)로 잡아 로컬 DB 의 실제 알림이
 * 섞이지 않게 하고, id 와 법정동 코드는 실제 값과 겹치지 않는 큰 값(99001~99003 구)을 쓴다.
 *
 * <p>시군구 관계는 평가 셋 272쌍을 뽑은 정의(rubric.md "구성 — 272쌍")와 같다: 수신 시군구(disaster_alert_region 앞 5자리)를
 * 하나라도 공유하면 같음, 공유하지 않으면서 region_adjacency 에 간선이 있으면 이웃, 그 밖은 대상이 아니다.
 * 테스트 지역: 99001(기준) / 99002(99001 의 이웃) / 99003(이웃 아님).
 */
@IntegrationTest
@Transactional
class ClusteringDailyScoreServiceTest {

    private static final LocalDate DAY = LocalDate.of(2001, 3, 4);
    // 채점 대상 알림 X 는 항상 그날 12:00 에 발송된다
    private static final String X_TIME = "2001-03-04 12:00:00";

    private static final String SAME_AREA = "9900100000";
    private static final String NEIGHBOR_AREA = "9900200000";
    private static final String FAR_AREA = "9900300000";

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ClusteringDailyScoreService service;
    @Autowired
    ClusteringDailyScoreRepository scoreRepository;
    @Autowired
    ClusteringDailyScorePairRepository pairRepository;

    @MockitoBean
    ClusteringJudge judge;

    // 판사가 알림 id 별로 내줄 판정. 목록에 없는 쌍은 SAME. 키는 "X 가 아닌 쪽 알림 id" 이다(X 는 테스트마다 하나뿐).
    private final Map<Long, JudgeVerdict> verdictByOtherAlert = new HashMap<>();
    private long xId;

    @BeforeEach
    void seedPlacesAndJudge() {
        // 지역 3곳과 이웃 간선(99001-99002, region_adjacency 는 양방향이 각각 한 행). disaster_alert_region 이 법정동 FK 를 가져 먼저 넣는다
        for (String code : List.of(SAME_AREA, NEIGHBOR_AREA, FAR_AREA)) {
            jdbc.update("INSERT INTO legal_district(code, name, is_active, is_active_string) VALUES (?, ?, true, 'true')",
                    code, "채점테스트 " + code);
        }
        jdbc.update("INSERT INTO region_adjacency(region_code, neighbor_code) VALUES ('99001', '99002'), ('99002', '99001')");

        // 판사 목: 입력의 두 본문("알림-<id>")에서 id 를 읽어 X 가 아닌 쪽의 준비된 판정을 돌려준다. 쌍마다 호출 횟수는 verify 로 센다
        when(judge.judge(any())).thenAnswer(inv -> {
            JudgeInput input = inv.getArgument(0);
            long first = idOf(input.first().body());
            long second = idOf(input.second().body());
            long other = first == xId ? second : first;
            return new JudgeResult(verdictByOtherAlert.getOrDefault(other, JudgeVerdict.SAME), 100, 1);
        });
    }

    // 결정: 판사에게 묻는 쌍은 그날 사건 알림 X 와 "X 보다 앞선 168시간 안, 같은 유형" 알림 Y 중
    // (1) 시스템이 같은 이벤트에 넣은 것, (2) 같은/이웃 시군구인데 다른 이벤트에 있는 것뿐이다. 272쌍으로 잰 판사 일치율을
    // 운영 쌍에 그대로 붙이려고 평가 셋을 뽑은 틀과 맞춘 것이다. Red 에서는 뼈대가 아무것도 저장하지 않아 pair 행이 0개로 실패한다.
    @Test
    void 판정_대상은_168시간_안_같은_유형의_같은_이벤트_쌍과_같은_또는_이웃_시군구_다른_이벤트_쌍이다() {
        // 준비: 이벤트 3개. E1 = X 와 같은 이벤트, E2 = X 와 다른 이벤트, E3 = 호우 이벤트
        long e1 = event(9_200_000_001L);
        long e2 = event(9_200_000_002L);
        long e3 = event(9_200_000_003L);
        // 준비: 채점 대상 X (그날 12:00, 화재, 기준 지역, E1)
        xId = 9_200_000_101L;
        alert(xId, "화재", X_TIME, SAME_AREA, e1);
        // 준비: 대상 ① — X 와 같은 이벤트, 같은 유형, 24시간 전 (168시간 안)
        long sameEvent = 9_200_000_102L;
        alert(sameEvent, "화재", "2001-03-03 12:00:00", SAME_AREA, e1);
        // 준비: 대상 ② — 다른 이벤트이고 시군구가 같음 / 이웃(99002). 둘 다 같은 유형이고 168시간 안
        long otherEventSameArea = 9_200_000_103L;
        long otherEventNeighbor = 9_200_000_104L;
        alert(otherEventSameArea, "화재", "2001-03-03 08:00:00", SAME_AREA, e2);
        alert(otherEventNeighbor, "화재", "2001-03-02 12:00:00", NEIGHBOR_AREA, e2);
        // 준비: 대상이 아닌 것 — 같은 이벤트여도 12일 전이라 168시간을 넘음 / 다른 유형의 다른 이벤트 / 같은 유형이지만 이웃도 아닌 먼 지역의 다른 이벤트
        alert(9_200_000_105L, "화재", "2001-02-20 12:00:00", SAME_AREA, e1);
        alert(9_200_000_106L, "호우", "2001-03-03 12:00:00", SAME_AREA, e3);
        alert(9_200_000_107L, "화재", "2001-03-03 12:00:00", FAR_AREA, e2);

        // 실행
        service.score(DAY);

        // 검증: 저장된 쌍은 대상 3개뿐이고 X 가 작은 id 라 alert_a 에 온다. 제외한 3건이 섞이면 이 목록이 달라진다
        List<ClusteringDailyScorePair> pairs = pairRepository.findByScoreDate(DAY);
        assertThat(pairs).extracting(ClusteringDailyScorePair::getAlertA, ClusteringDailyScorePair::getAlertB)
                .containsExactlyInAnyOrder(
                        tuple(xId, sameEvent), tuple(xId, otherEventSameArea), tuple(xId, otherEventNeighbor));
        // 검증: 판사는 대상 쌍마다 정확히 한 번 불린다(유료 호출이라 대상이 아닌 쌍에 쓰거나 같은 쌍에 두 번 쓰면 안 된다)
        verify(judge, times(3)).judge(any());
    }

    // 결정: 판사 판정을 라벨로 보고 점수를 센다. 시스템 묶음+판사 DIFF = 과병합, 시스템 분리+판사 SAME = 과소병합이고,
    // UNSURE·형식 실패는 점수 분모에서 빼 따로 센다(섞으면 점수가 판사의 "모름"에 따라 흔들린다). 판사 버전과 그 버전의 272쌍 일치율을
    // 그날 값으로 복사해 둬야 점수를 얼마나 믿을지 알 수 있다. Red 에서는 day 행이 저장되지 않아 첫 단언에서 실패한다.
    @Test
    void 판사_판정을_점수로_세어_저장하고_불일치_쌍에_종류와_당시_이벤트를_남긴다() {
        // 준비: E1 = X 와 같은 이벤트, E2 = 다른 이벤트
        long e1 = event(9_200_001_001L);
        long e2 = event(9_200_001_002L);
        xId = 9_200_001_101L;
        alert(xId, "화재", X_TIME, SAME_AREA, e1);
        // 준비: X 와 같은 이벤트(E1)의 같은 유형 알림 3개 — 판사 SAME(맞게 묶음) / DIFF(과병합) / UNSURE(분모에서 제외)
        long merged = 9_200_001_102L;
        long overMerged = 9_200_001_103L;
        long unsure = 9_200_001_104L;
        alert(merged, "화재", "2001-03-03 10:00:00", SAME_AREA, e1);
        alert(overMerged, "화재", "2001-03-03 11:00:00", SAME_AREA, e1);
        alert(unsure, "화재", "2001-03-03 12:00:00", SAME_AREA, e1);
        // 준비: 다른 이벤트(E2)의 같은 유형 알림 3개 — 판사 SAME(과소병합) / DIFF(제대로 나눔, 어느 쪽에도 안 셈) / 형식 실패(분모에서 제외)
        long missed = 9_200_001_105L;
        long separated = 9_200_001_106L;
        long formatFailure = 9_200_001_107L;
        alert(missed, "화재", "2001-03-03 13:00:00", SAME_AREA, e2);
        alert(separated, "화재", "2001-03-03 14:00:00", NEIGHBOR_AREA, e2);
        alert(formatFailure, "화재", "2001-03-03 15:00:00", NEIGHBOR_AREA, e2);
        verdictByOtherAlert.put(merged, JudgeVerdict.SAME);
        verdictByOtherAlert.put(overMerged, JudgeVerdict.DIFF);
        verdictByOtherAlert.put(unsure, JudgeVerdict.UNSURE);
        verdictByOtherAlert.put(missed, JudgeVerdict.SAME);
        verdictByOtherAlert.put(separated, JudgeVerdict.DIFF);
        verdictByOtherAlert.put(formatFailure, JudgeVerdict.FORMAT_FAILURE);

        // 실행
        service.score(DAY);

        // 검증: 그날 행이 저장된다
        Optional<ClusteringDailyScore> saved = scoreRepository.findById(DAY);
        assertThat(saved).isPresent();
        ClusteringDailyScore day = saved.get();
        // 검증: 사건 알림은 X 하나, 판정한 쌍은 6개(UNSURE·형식 실패 포함)
        assertThat(day.getIncidentCount()).isEqualTo(1);
        assertThat(day.getPairCount()).isEqualTo(6);
        // 검증: 맞게 묶음 1(merged), 과병합 1(overMerged), 과소병합 1(missed). 제대로 나눈 separated 는 어디에도 안 센다
        assertThat(day.getCorrectlyMerged()).isEqualTo(1);
        assertThat(day.getOverMerged()).isEqualTo(1);
        assertThat(day.getMissed()).isEqualTo(1);
        // 검증: UNSURE 와 형식 실패는 원인이 달라 각각 따로 센다
        assertThat(day.getUnsureCount()).isEqualTo(1);
        assertThat(day.getFormatFailureCount()).isEqualTo(1);
        // 검증: 판사 버전과 그 버전의 일치율 4개를 복사했다(상수를 직접 적지 않고 판사의 값과 견줘 버전이 올라도 깨지지 않게 한다)
        Agreement agreement = ClusteringJudge.agreementOf(ClusteringJudge.PROMPT_VERSION).orElseThrow();
        assertThat(day.getJudgeVersion()).isEqualTo(ClusteringJudge.PROMPT_VERSION);
        assertThat(day.getAgreementSameNum()).isEqualTo(agreement.sameNum());
        assertThat(day.getAgreementSameDen()).isEqualTo(agreement.sameDen());
        assertThat(day.getAgreementDiffNum()).isEqualTo(agreement.diffNum());
        assertThat(day.getAgreementDiffDen()).isEqualTo(agreement.diffDen());
        // 검증: 비용 확인용 토큰은 목 응답(호출당 100/1)을 6번 더한 값
        assertThat(day.getPromptTokens()).isEqualTo(600);
        assertThat(day.getCompletionTokens()).isEqualTo(6);

        // 검증: 쌍마다 (판정, 시스템이 같은 이벤트에 넣었는가, 당시 이벤트 id 두 개, 불일치 종류)를 남긴다.
        // 불일치는 과병합(시스템 묶음+DIFF)과 과소병합(시스템 분리+SAME)뿐이고, 맞춘 쌍·UNSURE·형식 실패는 null 이다
        List<ClusteringDailyScorePair> pairs = pairRepository.findByScoreDate(DAY);
        assertThat(pairs).extracting(ClusteringDailyScorePair::getAlertB, ClusteringDailyScorePair::getJudgeVerdict,
                        ClusteringDailyScorePair::isSystemSameEvent, ClusteringDailyScorePair::getEventA,
                        ClusteringDailyScorePair::getEventB, ClusteringDailyScorePair::getDisagreement)
                .containsExactlyInAnyOrder(
                        tuple(merged, JudgeVerdict.SAME, true, e1, e1, null),
                        tuple(overMerged, JudgeVerdict.DIFF, true, e1, e1, Disagreement.OVER_MERGED),
                        tuple(unsure, JudgeVerdict.UNSURE, true, e1, e1, null),
                        tuple(missed, JudgeVerdict.SAME, false, e1, e2, Disagreement.MISSED),
                        tuple(separated, JudgeVerdict.DIFF, false, e1, e2, null),
                        tuple(formatFailure, JudgeVerdict.FORMAT_FAILURE, false, e1, e2, null));
    }

    /** 이벤트 행 하나. disaster_alert.event_id 가 이벤트를 외래 키로 참조하므로 알림보다 먼저 넣는다. */
    private long event(long id) {
        jdbc.update("INSERT INTO disaster_events(id, event_title, first_alert_at, last_alert_at, alert_count) "
                + "VALUES (?, '일일 채점 테스트 이벤트', '2001-03-01 00:00:00', '2001-03-04 12:00:00', 1)", id);
        return id;
    }

    /** 사건(INCIDENT) 알림 하나와 수신 지역 하나. 본문은 목 판사가 id 를 읽을 수 있게 "알림-id" 로 둔다. */
    private void alert(long id, String type, String createdAt, String regionCode, long eventId) {
        jdbc.update("INSERT INTO disaster_alert(disaster_alert_id, sn, message, created_at, disaster_type, alert_kind, "
                        + "event_id, event_sequence_no) VALUES (?, ?, ?, CAST(? AS timestamp), ?, 'INCIDENT', ?, 1)",
                id, id, "알림-" + id, createdAt, type, eventId);
        jdbc.update("INSERT INTO disaster_alert_region(disaster_alert_id, legal_district_code) VALUES (?, ?)", id, regionCode);
    }

    private static long idOf(String body) {
        return Long.parseLong(body.substring("알림-".length()));
    }
}
