package com.disaster.alert.alertapi.domain.disasteralert.tool;

import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.disasteralert.service.AlertKindService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 이미 저장된 재난문자 중 alert_kind 가 비어 있는(미분류) 알림을 사건/비사건으로 분류해 채우는 백필 도구.
 * 새 알림은 수집 루프가 분류하므로, 이 도구는 분류 기능 배포 전에 쌓인 알림용이다.
 *
 * <p><b>활성 조건</b>: Spring Profile {@code alert-kind-backfill} 활성 시에만 실행.
 *
 * <p><b>옵션</b>
 * <ul>
 *   <li>{@code --alert-kind.limit=N}: 이번 실행에서 분류할 최대 건수(비용 상한). 없으면 전부</li>
 *   <li>{@code --alert-kind.threads=N}: 동시 호출 수(기본 4)</li>
 * </ul>
 * 미분류만 대상으로 삼으므로 중간에 멈춰도 같은 명령으로 다시 실행하면 이어서 진행한다(이미 분류한 알림은 다시 과금하지 않는다).
 * 분류 실패(OpenAI 오류·속도 제한 등)는 미분류로 남으므로, 다시 실행하면 그 알림만 재시도된다.
 *
 * <p><b>실행 예</b> (알림 1건당 gpt-4o-mini 1회 호출, 실측 약 $0.0003/건)
 * <pre>
 * cd backend && set -a && source ../.env.dev && set +a
 * ./gradlew bootRun --args='--spring.profiles.active=alert-kind-backfill --alert-kind.limit=100'   # 먼저 소량
 * ./gradlew bootRun --args='--spring.profiles.active=alert-kind-backfill'                         # 나머지 전부
 * </pre>
 */
@Component
@Profile("alert-kind-backfill")
@RequiredArgsConstructor
@Slf4j
public class AlertKindBackfillTool implements ApplicationRunner {

    private static final int DEFAULT_THREADS = 4;
    private static final int PROGRESS_EVERY = 500;

    private final DisasterAlertRepository disasterAlertRepository;
    private final AlertKindService alertKindService;

    @Override
    public void run(ApplicationArguments args) {
        int limit = intOption(args, "alert-kind.limit", Integer.MAX_VALUE);
        int threads = intOption(args, "alert-kind.threads", DEFAULT_THREADS);

        // 1) 미분류 id 를 오름차순으로 가져와 앞에서부터 limit 건만 자른다(비용 상한)
        List<Long> unclassified = disasterAlertRepository.findUnclassifiedIds();
        List<Long> targets = unclassified.subList(0, Math.min(limit, unclassified.size()));
        log.info("AlertKindBackfillTool 시작: 미분류 {}건 중 {}건 분류 (threads={})",
                unclassified.size(), targets.size(), threads);

        // 2) 알림마다 작업 1개 — 같은 id 가 두 번 제출되지 않으므로 중복 과금이 없다.
        //    classifyAndSave 는 예외를 삼키고(실패=미분류 유지) 한 건 실패가 나머지를 막지 않는다.
        AtomicInteger done = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        long t0 = System.currentTimeMillis();
        for (Long id : targets) {
            pool.submit(() -> {
                alertKindService.classifyAndSave(id);
                int n = done.incrementAndGet();
                if (n % PROGRESS_EVERY == 0) {
                    log.info("alert-kind 백필 진행: {}/{} (elapsed={}s)",
                            n, targets.size(), (System.currentTimeMillis() - t0) / 1000);
                }
            });
        }

        // 3) 제출한 작업이 모두 끝날 때까지 기다린다
        pool.shutdown();
        try {
            pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        // 4) done 은 "시도" 건수라 성공 여부를 모른다 — 남은 미분류를 다시 세어 실제로 채워진 수를 낸다.
        //    (키 만료·잔고 소진이면 전부 실패해도 시도 건수만 보면 끝난 것처럼 보인다)
        //    근사치다 — 실행 중 수집 루프가 새 알림을 저장·분류하면 몇 건 오차가 난다.
        int remaining = disasterAlertRepository.findUnclassifiedIds().size();
        int filled = unclassified.size() - remaining;
        log.info("AlertKindBackfillTool 완료: {}건 시도, {}건 분류됨, 남은 미분류 {}건, 소요={}s — 다시 실행하면 남은 건만 이어서 분류한다",
                done.get(), filled, remaining, (System.currentTimeMillis() - t0) / 1000);
        if (done.get() > 0 && filled == 0) {
            log.warn("AlertKindBackfillTool: 시도한 {}건이 하나도 분류되지 않았다 — OpenAI 키·잔고·속도 제한을 확인할 것", done.get());
        }
    }

    /** 옵션 값이 없거나 숫자가 아니면 기본값. 0 이하도 기본값으로 본다. */
    private static int intOption(ApplicationArguments args, String name, int defaultValue) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.isEmpty()) {
            return defaultValue;
        }
        try {
            int v = Integer.parseInt(values.get(0).trim());
            return v > 0 ? v : defaultValue;
        } catch (NumberFormatException e) {
            log.warn("--{} 값이 숫자가 아니라 기본값 {} 을 씁니다: {}", name, defaultValue, values.get(0));
            return defaultValue;
        }
    }
}
