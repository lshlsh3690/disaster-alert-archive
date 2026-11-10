package com.disaster.alert.alertapi.domain.event.scheduler;

import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyCheck;
import com.disaster.alert.alertapi.domain.event.service.ClusteringDailyCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 사건 알림의 클러스터링 구조 검사를 매일 돌린다.
 *
 * <p>최근 7일을 다시 검사한다. 00:30 KST 에 도는 이유: 자정 직전에 수집된 알림의 클러스터링이 끝난 뒤에 세기 위한 여유 시간이다.
 *
 * <p>{@code @Async} 로 별도 실행기에서 돈다. 스케줄러 스레드가 1개라 여기서 시간을 쓰면 매분 도는 재난문자 수집이 밀린다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClusteringDailyCheckScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int RECHECK_DAYS = 7;

    private final ClusteringDailyCheckService checkService;

    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Seoul")
    @Async("clusteringCheckExecutor")
    public void runDaily() {
        LocalDate yesterday = LocalDate.now(KST).minusDays(1);
        try {
            // 최근 7일을 다시 세어 놓친 날을 채우고, 늦게 수집된 알림도 반영한다. 재시작으로 00:30 을 지나쳐도 다음 실행이 메운다.
            List<ClusteringDailyCheck> flagged = checkService.checkRecent(yesterday, RECHECK_DAYS);
            // 미배정은 이벤트 화면에서 알림이 사라진다는 뜻이고 정상 상태는 0건이라 사람이 봐야 한다.
            // 같은 미배정을 매일 다시 알리지 않도록 새로 잡혔거나 늘어난 날만 error 로 남긴다.
            for (ClusteringDailyCheck c : flagged) {
                log.error("클러스터링 일일 검사: {} 사건 {}건 중 이벤트 미배정 {}건 (임베딩 없음 {}건)",
                        c.getCheckDate(), c.getIncidentCount(), c.getUnassignedCount(), c.getNoEmbeddingCount());
            }
            log.info("클러스터링 일일 검사 완료: {} 까지 {}일, 새로 잡힌 미배정 {}일", yesterday, RECHECK_DAYS, flagged.size());
        } catch (Exception e) {
            log.error("클러스터링 일일 검사 실패: targetDate={}", yesterday, e);
        }
    }
}
