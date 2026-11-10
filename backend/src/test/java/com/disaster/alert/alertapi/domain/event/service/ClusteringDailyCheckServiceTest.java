package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyCheck;
import com.disaster.alert.alertapi.domain.event.repository.ClusteringDailyCheckRepository;
import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 SQL(날짜 범위, 매핑 없음, 임베딩 없음)이 핵심이라 DB 가 필요하다. 테스트마다 트랜잭션이 롤백되므로
 * 넣은 알림·이벤트는 남지 않는다. 검사 날짜는 실제 알림이 없을 과거 날짜(2001-03-04)로 잡아, 로컬 DB 에 이미
 * 있는 알림이 건수에 섞이지 않게 한다. id/sn 은 다른 테스트·실제 데이터와 겹치지 않게 큰 값을 쓴다.
 */
@IntegrationTest
@Transactional
class ClusteringDailyCheckServiceTest {

    private static final LocalDate DAY = LocalDate.of(2001, 3, 4);
    private static final long EVENT_ID = 9_100_000_001L;

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ClusteringDailyCheckService service;
    @Autowired
    ClusteringDailyCheckRepository repository;

    @BeforeEach
    void seedEvent() {
        // 배정된 알림이 매달릴 이벤트 하나 (event_alert_mapping 이 이벤트를 외래 키로 참조한다)
        jdbc.update("INSERT INTO disaster_events(id, event_title, first_alert_at, last_alert_at, alert_count) "
                + "VALUES (?, '일일 검사 테스트 이벤트', '2001-03-04 10:00:00', '2001-03-04 10:00:00', 1)", EVENT_ID);
    }

    // 결정: 검사는 "그 날짜의 사건(INCIDENT) 알림"만 대상으로 센다. 안내(ADVISORY)는 이벤트에 안 들어가는 게 정상이라
    // 세면 미배정이 부풀고, 다른 날짜 알림이 섞이면 날짜별 숫자를 믿을 수 없다.
    @Test
    void 그_날짜의_INCIDENT_알림만_대상으로_미배정과_임베딩없음을_센다() {
        // 준비: 대상 2건 = 미배정+임베딩 없음 1건, 배정+임베딩 있음 1건
        insertAlert(9_100_000_101L, "INCIDENT", "2001-03-04 09:00:00", null);
        insertAlert(9_100_000_102L, "INCIDENT", "2001-03-04 23:59:00", vector());
        assign(9_100_000_102L);
        // 준비: 대상이 아닌 2건 = 같은 날 ADVISORY 미배정(종류가 달라 제외), 전날 INCIDENT 미배정(날짜가 달라 제외)
        insertAlert(9_100_000_103L, "ADVISORY", "2001-03-04 12:00:00", null);
        insertAlert(9_100_000_104L, "INCIDENT", "2001-03-03 12:00:00", null);

        // 실행
        ClusteringDailyCheck result = service.check(DAY);

        // 검증: 대상 2건 중 미배정 1건(101), 임베딩 없음 1건(101). 제외한 103·104 가 섞이면 이 숫자가 달라진다.
        assertThat(result.getIncidentCount()).isEqualTo(2);
        assertThat(result.getUnassignedCount()).isEqualTo(1);
        assertThat(result.getNoEmbeddingCount()).isEqualTo(1);
    }

    // 결정: 같은 날짜를 다시 검사해도 줄은 하나이고 최신 건수로 갱신된다. 스케줄러 재시도·수동 재실행이
    // 날짜마다 중복 줄을 만들면 "하루 한 줄" 로그 기준이 깨진다.
    @Test
    void 같은_날짜를_두_번_돌리면_줄은_하나이고_늘어난_건수로_갱신된다() {
        // 준비: 첫 실행 때 대상은 미배정 1건뿐
        insertAlert(9_100_000_201L, "INCIDENT", "2001-03-04 09:00:00", null);
        service.check(DAY);

        // 준비: 첫 실행 뒤 같은 날짜의 미배정 알림이 하나 더 들어왔다고 가정
        insertAlert(9_100_000_202L, "INCIDENT", "2001-03-04 10:00:00", null);

        // 실행: 같은 날짜로 한 번 더
        service.check(DAY);

        // 검증: 그 날짜의 줄은 1개이고, 두 번째 실행의 건수(대상 2, 미배정 2)로 덮어써져 있다
        assertThat(repository.findAll().stream().filter(c -> DAY.equals(c.getCheckDate())).count()).isEqualTo(1);
        ClusteringDailyCheck saved = repository.findById(DAY).orElseThrow();
        assertThat(saved.getIncidentCount()).isEqualTo(2);
        assertThat(saved.getUnassignedCount()).isEqualTo(2);
    }

    // 결정: 최근 N일을 통째로 다시 계산하므로, 앱이 꺼져 있어 검사 행이 빠진 날도 다음 실행에서 채워진다.
    // Red 에서는 뼈대가 아무것도 저장하지 않아, 가운데 날의 행이 없다는 단언(isPresent)에서 실패한다.
    @Test
    void 가운데_날의_검사_행이_빠져_있어도_최근_N일을_돌리면_채워진다() {
        // 준비: 연속 3일. 첫날·마지막날은 이미 검사돼 행이 있고(알림 없음, 0건), 가운데 날만 행이 없다
        LocalDate first = LocalDate.of(2001, 6, 1);
        LocalDate middle = first.plusDays(1);
        LocalDate last = first.plusDays(2);
        repository.save(ClusteringDailyCheck.of(first, 0, 0, 0, LocalDateTime.of(2001, 6, 2, 0, 30)));
        repository.save(ClusteringDailyCheck.of(last, 0, 0, 0, LocalDateTime.of(2001, 6, 4, 0, 30)));
        // 준비: 빠진 가운데 날에 사건 알림 2건 - 미배정 1건(301), 배정 1건(302)
        insertAlert(9_100_000_301L, "INCIDENT", "2001-06-02 09:00:00", vector());
        insertAlert(9_100_000_302L, "INCIDENT", "2001-06-02 15:00:00", vector());
        assign(9_100_000_302L);

        // 실행: 마지막 날을 포함해 과거로 3일
        service.checkRecent(last, 3);

        // 검증: 세 날 모두 행이 있고, 빠졌던 가운데 날에는 실제 건수(대상 2, 미배정 1)가 채워졌다
        assertThat(repository.findById(first)).isPresent();
        assertThat(repository.findById(last)).isPresent();
        assertThat(repository.findById(middle)).isPresent();
        ClusteringDailyCheck filled = repository.findById(middle).orElseThrow();
        assertThat(filled.getIncidentCount()).isEqualTo(2);
        assertThat(filled.getUnassignedCount()).isEqualTo(1);
    }

    // 결정: 매일 7일을 다시 세도 같은 미배정에 경보가 반복되면 안 된다. 그래서 반환값은 "새로 잡혔거나 이전 저장값보다
    // 늘어난" 날만 담고, 같은 값이거나 0건인 날은 뺀다. 저장은 반환 여부와 상관없이 네 날 모두 최신 값이어야 한다.
    // Red 에서는 뼈대가 빈 목록을 돌려줘 "B, C 가 들어 있다"는 단언에서 실패한다.
    @Test
    void 미배정이_새로_잡혔거나_늘어난_날만_돌려주고_저장은_모든_날_최신값이다() {
        // 연속 4일: A(06-10) B(06-11) C(06-12) D(06-13)
        LocalDate a = LocalDate.of(2001, 6, 10);
        LocalDate b = a.plusDays(1);
        LocalDate c = a.plusDays(2);
        LocalDate d = a.plusDays(3);
        // 준비: A·B 는 이전 검사에서 미배정 1건으로 저장돼 있다. C·D 는 저장 행이 없다
        LocalDateTime before = LocalDateTime.of(2001, 6, 13, 0, 30);
        repository.save(ClusteringDailyCheck.of(a, 1, 1, 1, before));
        repository.save(ClusteringDailyCheck.of(b, 1, 1, 1, before));
        // 준비 A: 알림 그대로 미배정 1건 -> 저장값과 같아서 돌려주지 않는다
        insertAlert(9_100_000_401L, "INCIDENT", "2001-06-10 09:00:00", null);
        // 준비 B: 저장 뒤 수집이 늦게 들어와 미배정 2건 -> 1에서 2로 늘어서 돌려준다
        insertAlert(9_100_000_411L, "INCIDENT", "2001-06-11 09:00:00", null);
        insertAlert(9_100_000_412L, "INCIDENT", "2001-06-11 10:00:00", null);
        // 준비 C: 저장 행 없이 미배정 1건 -> 새로 잡힌 것이라 돌려준다
        insertAlert(9_100_000_421L, "INCIDENT", "2001-06-12 09:00:00", null);
        // 준비 D: 알림이 있지만 전부 배정됨(미배정 0건) -> 행은 저장하되 돌려주지 않는다
        insertAlert(9_100_000_431L, "INCIDENT", "2001-06-13 09:00:00", vector());
        assign(9_100_000_431L);

        // 실행
        List<ClusteringDailyCheck> flagged = service.checkRecent(d, 4);

        // 검증: 돌려준 날은 B, C 뿐 (A 는 같은 값, D 는 0건이라 제외)
        assertThat(flagged).extracting(ClusteringDailyCheck::getCheckDate).containsExactlyInAnyOrder(b, c);
        // 검증: 저장 행은 네 날 모두 최신 미배정 건수 (A 1, B 2, C 1, D 0)
        assertThat(repository.findById(a).orElseThrow().getUnassignedCount()).isEqualTo(1);
        assertThat(repository.findById(b).orElseThrow().getUnassignedCount()).isEqualTo(2);
        assertThat(repository.findById(c).orElseThrow().getUnassignedCount()).isEqualTo(1);
        assertThat(repository.findById(d).orElseThrow().getUnassignedCount()).isZero();
    }

    private void insertAlert(long id, String kind, String createdAt, String embedding) {
        jdbc.update("INSERT INTO disaster_alert(disaster_alert_id, sn, message, created_at, disaster_type, alert_kind, embedding) "
                        + "VALUES (?, ?, '테스트 본문', CAST(? AS timestamp), '화재', ?, CAST(? AS vector))",
                id, id, createdAt, kind, embedding);
    }

    private void assign(long alertId) {
        jdbc.update("INSERT INTO event_alert_mapping(event_id, alert_id, sequence_no) VALUES (?, ?, 1)",
                EVENT_ID, alertId);
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
