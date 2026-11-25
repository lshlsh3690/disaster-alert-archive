package com.disaster.alert.alertapi.domain.event.scheduler;

import com.disaster.alert.alertapi.domain.event.repository.ClusteringDailyScoreRepository;
import com.disaster.alert.alertapi.domain.event.service.ClusteringDailyScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 어제까지의 사건 알림 클러스터링을 판사(LLM)로 매일 채점한다.
 *
 * <p>일일 구조 검사(00:30)가 끝난 01:00 KST 에 돈다. 최근 7일 중 {@code clustering_daily_score} 에 기록이 없는 날만 채점한다 -
 * 채점은 유료라 같은 날을 두 번 판정하지 않고, 앱이 꺼져 빠진 날은 다음 실행이 메운다(R7).
 *
 * <p><b>{@code prod} 프로필에서만 돈다.</b> 유료 기능이라 로컬·테스트에서는 빈이 만들어지지 않아 호출이 나갈 수 없고,
 * 운영에서는 끄는 설정 없이 항상 돈다.
 *
 * <p>{@code @Async} 로 별도 실행기에서 돈다. 스케줄러 스레드가 1개라 하루치 판정(수백 번의 순차 LLM 호출)이 매분 도는
 * 재난문자 수집을 밀지 않게 하려는 것이다.
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
@Slf4j
public class ClusteringDailyScoreScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int LOOKBACK_DAYS = 7;

    private final ClusteringDailyScoreService scoreService;
    private final ClusteringDailyScoreRepository scoreRepository;

    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
    @Async("clusteringScoreExecutor")
    public void runDaily() {
        // 1) 7일 전부터 어제까지 오래된 날부터 본다. 오늘(i=0)은 아직 끝나지 않아 넣지 않는다
        LocalDate today = LocalDate.now(KST);
        for (int i = LOOKBACK_DAYS; i >= 1; i--) {
            LocalDate date = today.minusDays(i);
            // 2) 한 날의 실패가 다른 날을 막지 않게 날짜마다 잡는다. 기록 조회(DB)도 같은 경계 안에 둬서, 조회가 일시적으로
            //    실패해도 뒤의 날짜는 계속 처리한다. 실패한 날은 기록이 없어 다음 실행이 다시 채점한다
            try {
                // 이미 채점한 날은 건너뛴다(유료 호출을 두 번 쓰지 않는다)
                if (scoreRepository.existsById(date)) {
                    continue;
                }
                scoreService.score(date);
            } catch (Exception e) {
                logFailure(date, i == LOOKBACK_DAYS, e);
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
}
