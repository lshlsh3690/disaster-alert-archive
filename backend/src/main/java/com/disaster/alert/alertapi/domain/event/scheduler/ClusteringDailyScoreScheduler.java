package com.disaster.alert.alertapi.domain.event.scheduler;

import com.disaster.alert.alertapi.domain.event.repository.ClusteringDailyScoreRepository;
import com.disaster.alert.alertapi.domain.event.service.ClusteringDailyScoreService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * 어제까지의 사건 알림 클러스터링을 판사(LLM)로 매일 채점한다.
 *
 * <p>일일 구조 검사(00:30)가 끝난 01:00 KST 에 돈다. 최근 7일 중 {@code clustering_daily_score} 에 기록이 없는 날만 채점한다 -
 * 채점은 유료라 같은 날을 두 번 판정하지 않고, 앱이 꺼져 빠진 날은 다음 실행이 메운다(R7).
 *
 * <p><b>채점 시작일({@code clustering.daily-score.start-date}) 설정이 비어 있으면 아무것도 채점하지 않는다.</b> 이 기능은 유료라
 * 운영 도입 승인 전에 배포돼도 비용이 나가면 안 되기 때문이다. 값이 있으면 그 날짜 이전은 찾지 않는다 - 첫 실행에는 기록이 하나도
 * 없어 하한이 없으면 도입 전 7일까지 유료 호출하게 되고, 도입 시점부터만 채점한다는 결정(FR-020)과 어긋난다.
 *
 * <p>{@code @Async} 로 별도 실행기에서 돈다. 스케줄러 스레드가 1개라 하루치 판정(수백 번의 순차 LLM 호출)이 매분 도는
 * 재난문자 수집을 밀지 않게 하려는 것이다.
 */
@Component
@Slf4j
public class ClusteringDailyScoreScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int LOOKBACK_DAYS = 7;

    private final ClusteringDailyScoreService scoreService;
    private final ClusteringDailyScoreRepository scoreRepository;
    private final String startDateSetting;
    // 시작일은 기동 때 한 번 해석한다. 비었거나 형식이 틀리면 null(= 꺼짐) - 기동 로그와 실제 동작이 어긋나지 않게
    private final LocalDate startDate;

    // Lombok 생성자는 final 필드의 @Value 를 옮기지 않아 직접 쓴다
    public ClusteringDailyScoreScheduler(ClusteringDailyScoreService scoreService,
                                         ClusteringDailyScoreRepository scoreRepository,
                                         @Value("${clustering.daily-score.start-date:}") String startDateSetting) {
        this.scoreService = scoreService;
        this.scoreRepository = scoreRepository;
        this.startDateSetting = startDateSetting == null ? "" : startDateSetting.trim();
        this.startDate = parseStartDate(this.startDateSetting);
    }

    /** 켜졌는지 시작할 때 한 번 남긴다. 운영 로그에서 "왜 채점이 안 도는가"를 가장 먼저 확인할 곳이다. */
    @PostConstruct
    void logStartup() {
        if (startDateSetting.isEmpty()) {
            log.info("클러스터링 일일 채점 꺼짐: clustering.daily-score.start-date(CLUSTERING_DAILY_SCORE_START_DATE) 가 비어 있다");
        } else if (startDate == null) {
            // 설정 오타가 조용히 채점 중단으로 남지 않도록 error 로 남긴다(사람이 고쳐야 하는 일)
            log.error("클러스터링 일일 채점 꺼짐: 시작일 형식 오류(yyyy-MM-dd 여야 함): '{}'", startDateSetting);
        } else {
            log.info("클러스터링 일일 채점 켜짐: 시작일 {}", startDate);
        }
    }

    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
    @Async("clusteringScoreExecutor")
    public void runDaily() {
        // 1) 시작일이 비었거나 형식이 틀리면 채점하지 않는다 - 돈이 나가는 쪽보다 안 도는 쪽이 안전한 방향이다
        if (startDate == null) {
            return;
        }
        // 2) 오늘은 아직 끝나지 않았으므로 어제부터 과거로 7일을 오래된 날부터 본다
        LocalDate yesterday = LocalDate.now(KST).minusDays(1);
        for (int i = LOOKBACK_DAYS - 1; i >= 0; i--) {
            LocalDate date = yesterday.minusDays(i);
            // 3) 도입일 이전은 건너뛴다
            if (date.isBefore(startDate)) {
                continue;
            }
            // 4) 한 날의 실패가 다른 날을 막지 않게 날짜마다 잡는다. 기록 조회(DB)도 같은 경계 안에 둬서, 조회가 일시적으로
            //    실패해도 뒤의 날짜는 계속 처리한다. 실패한 날은 기록이 없어 다음 실행이 다시 채점한다
            try {
                // 이미 채점한 날은 건너뛴다(유료 호출을 두 번 쓰지 않는다)
                if (scoreRepository.existsById(date)) {
                    continue;
                }
                scoreService.score(date);
            } catch (Exception e) {
                logFailure(date, i == LOOKBACK_DAYS - 1, e);
            }
        }
    }

    // 다음 실행이 다시 채점할 기회가 남았으면 warn, 이번이 7일 창의 마지막 기회였다면 error(그날은 다시는 채점되지 않아
    // 사람이 봐야 한다). CLAUDE.md 의 "사람이 조치할 것만 error" 기준이다. 같은 원인으로 계속 실패하면 그날을 판정할 때마다
    // 요금이 나가므로, warn 이 같은 날짜로 이어지면 원인을 먼저 보는 것이 좋다.
    private static void logFailure(LocalDate date, boolean lastChance, Exception e) {
        if (lastChance) {
            log.error("클러스터링 일일 채점 실패(마지막 기회 - 이 날은 다시 채점되지 않음): date={}", date, e);
        } else {
            log.warn("클러스터링 일일 채점 실패(다음 실행에서 다시 시도): date={}", date, e);
        }
    }

    private static LocalDate parseStartDate(String setting) {
        if (setting.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(setting);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
