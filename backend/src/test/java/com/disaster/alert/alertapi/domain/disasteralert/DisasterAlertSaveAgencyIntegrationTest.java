package com.disaster.alert.alertapi.domain.disasteralert;

import com.disaster.alert.alertapi.domain.disasteralert.repository.AgencyAliasRepository;
import com.disaster.alert.alertapi.domain.disasteralert.service.DisasterAlertService;
import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.doThrow;

/**
 * 수집 저장 경로({@code DisasterAlertService#saveData})에 발신 기관 지정이 제대로 붙었는지 실제 Postgres 로 검증한다.
 *
 * <p>일부러 {@code @Transactional} 을 붙이지 않는다 — 붙이면 saveData 가 테스트 트랜잭션에 합류해 "커밋 시점의
 * UnexpectedRollbackException" 이 드러나지 않는다. 대신 쓴 데이터(sn 은 운영과 겹치지 않는 큰 값, 기관 이름은
 * 테스트 전용 접두사)를 {@link #cleanUp()} 에서 직접 지운다.
 */
@IntegrationTest
class DisasterAlertSaveAgencyIntegrationTest {

    /** 운영 sn 대역과 겹치지 않는 큰 값. 테스트마다 이 둘만 쓰고 끝나면 지운다. */
    private static final long SN_WITH_LABEL = 990_100_001L;
    private static final long SN_WITHOUT_LABEL = 990_100_002L;
    /** 기관·표기 정리용 접두사 — 이 접두사로 시작하는 기관만 지운다. */
    private static final String LABEL_PREFIX = "테스트기관-저장";

    @Autowired
    private DisasterAlertService disasterAlertService;
    @Autowired
    private JdbcTemplate jdbc;
    /** 진짜 빈을 감싼 스파이 — AgencyService 는 진짜 빈(트랜잭션 프록시)이라 rollback-only 표시가 재현된다. */
    @MockitoSpyBean
    private AgencyAliasRepository agencyAliasRepository;

    @AfterEach
    void cleanUp() {
        // FK 순서: 알림(agency_id 참조) → 표기 → 기관
        jdbc.update("DELETE FROM disaster_alert WHERE sn IN (?, ?)", SN_WITH_LABEL, SN_WITHOUT_LABEL);
        jdbc.update("DELETE FROM agency_alias WHERE label LIKE ?", LABEL_PREFIX + "%");
        jdbc.update("DELETE FROM agency WHERE name LIKE ?", LABEL_PREFIX + "%");
    }

    /**
     * 기관은 부가 정보라, 해석이 실패해도 알림 수집은 막히면 안 된다. 그런데 saveData(@Transactional) 안에서
     * AgencyService(@Transactional, 같은 트랜잭션에 합류)가 예외를 내면 그 프록시가 공유 트랜잭션을 rollback-only 로
     * 표시한다 — saveData 쪽에서 예외를 삼켜도 커밋 때 UnexpectedRollbackException 이 나서 그 회차 알림이 통째로 사라진다.
     * 현재 구현은 해석을 같은 트랜잭션에서 호출하므로 아래 doesNotThrow 단언에서 UnexpectedRollbackException 으로 실패해야 한다.
     */
    @Test
    @DisplayName("기관 해석이 예외를 내도 saveData 는 알림을 기관 없이 저장한다")
    void 기관_해석이_실패해도_알림은_저장된다() {
        // 준비: 표기 조회(findAllById)가 런타임 예외를 던지게 한다 — AgencyService 가 진짜 빈이라 그 안에서 터진다
        doThrow(new IllegalStateException("기관 표기 조회 실패(테스트)"))
                .when(agencyAliasRepository).findAllById(anyIterable());

        // 실행 + 검증 1: 해석이 실패해도 saveData 가 예외 없이 끝나야 한다(현재는 커밋 시 UnexpectedRollbackException)
        assertThatCode(() -> disasterAlertService.saveData(rawResponse(
                alertJson(SN_WITH_LABEL, "호우 안내입니다. [" + LABEL_PREFIX + "실패]"))))
                .doesNotThrowAnyException();

        // 검증 2: 알림은 저장돼 있고 기관은 지정되지 않았다(null)
        assertThat(countBySn(SN_WITH_LABEL)).isEqualTo(1);
        assertThat(agencyNameOf(SN_WITH_LABEL)).isNull();
    }

    /**
     * 정상 경로 배선 확인: saveData 가 해석 결과를 알림에 실제로 연결하는지 본다. 기관 지정 코드를 통째로 지워도
     * 다른 테스트가 모두 통과하는 공백을 메운다(구현이 이미 있어 Red 단계에서도 통과할 수 있다).
     */
    @Test
    @DisplayName("표기 있는 알림은 그 표기의 기관이 지정되고, 표기 없는 알림은 기관이 null 이다")
    void 표기_있는_알림에만_기관이_지정된다() {
        // 실행: 표기 있는 본문 1건과 대괄호가 없는 본문 1건을 한 응답으로 저장
        disasterAlertService.saveData(rawResponse(
                alertJson(SN_WITH_LABEL, "대설 주의보입니다. [" + LABEL_PREFIX + "정상]"),
                alertJson(SN_WITHOUT_LABEL, "발신 기관 표시가 없는 문자입니다.")));

        // 검증: 앞의 알림은 표기 이름의 기관, 뒤의 알림은 null
        assertThat(agencyNameOf(SN_WITH_LABEL)).isEqualTo(LABEL_PREFIX + "정상");
        assertThat(countBySn(SN_WITHOUT_LABEL)).isEqualTo(1);
        assertThat(agencyNameOf(SN_WITHOUT_LABEL)).isNull();
    }

    /** 공공데이터포털 응답 형태: header.resultCode "00" 이어야 저장하고, body 는 알림 배열이다. */
    private static String rawResponse(String... alerts) {
        return "{\"header\":{\"resultMsg\":\"NORMAL SERVICE\",\"resultCode\":\"00\",\"errorMsg\":null},"
                + "\"numOfRows\":" + alerts.length + ",\"pageNo\":1,\"totalCount\":" + alerts.length + ","
                + "\"body\":[" + String.join(",", alerts) + "]}";
    }

    /** 수신지역명은 법정동에 없는 값 — 지역 연결·날씨 집계 행이 생기지 않아 정리할 것이 알림 행뿐이다. 날짜는 실제 사건이 없는 1999년. */
    private static String alertJson(long sn, String message) {
        return "{\"SN\":" + sn + ",\"MSG_CN\":\"" + message + "\",\"RCPTN_RGN_NM\":\"테스트지역없음\","
                + "\"CRT_DT\":\"1999/07/01 09:00:00\",\"EMRG_STEP_NM\":\"안전안내\",\"DST_SE_NM\":\"호우\","
                + "\"MDFCN_YMD\":null}";
    }

    private int countBySn(long sn) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM disaster_alert WHERE sn = ?", Integer.class, sn);
    }

    /** 알림의 기관 이름(기관이 없으면 null). 지연 로딩 없이 DB 에서 바로 읽는다. */
    private String agencyNameOf(long sn) {
        List<String> names = jdbc.query(
                "SELECT a.name FROM disaster_alert d LEFT JOIN agency a ON a.id = d.agency_id WHERE d.sn = ?",
                (rs, i) -> rs.getString(1), sn);
        return names.isEmpty() ? null : names.get(0);
    }
}
