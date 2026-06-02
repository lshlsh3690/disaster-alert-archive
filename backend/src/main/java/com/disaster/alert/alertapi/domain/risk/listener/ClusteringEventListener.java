package com.disaster.alert.alertapi.domain.risk.listener;

import com.disaster.alert.alertapi.domain.risk.event.AlertClusteredEvent;
import com.disaster.alert.alertapi.domain.risk.service.RiskCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Set;

/**
 * 클러스터링 완료 → 위험도 재계산 트리거.
 *
 * <p>클러스터링 @Transactional 커밋 <b>이후</b>(AFTER_COMMIT)에 실행해야
 * disaster_events / event_alert_mapping 행이 보장된다. 평범한 @EventListener + @Async 는
 * 커밋 전 읽기 레이스를 일으킴.
 *
 * <p>@Async 실행: {@code @EnableAsync} 는 전역 {@code global.config.AsyncConfig} 에 이미 선언돼 있다.
 * executor 빈이 여러 개(translationExecutor, riskTaskExecutor)라 이름 없는 {@code @Async} 는
 * {@code SimpleAsyncTaskExecutor} 로 fallback 되므로, 위험도 전용 풀
 * ({@code RiskAsyncConfig#riskTaskExecutor} — core2/max4/queue500)을 쓰도록 이름을 명시한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClusteringEventListener {

    private final RiskCalculationService riskService;

    @Async("riskTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAlertClustered(AlertClusteredEvent event) {
        try {
            // 1. 이벤트 영향(impact) 재계산 → 영향받은 시군구 집합 반환
            Set<String> affectedSigungu = riskService.recomputeEventRisk(event.eventId());

            // 2. 영향받은 시군구만 즉시 region_risk_index 재계산 (문자 도착 시 실시간 갱신).
            //    리스너는 외부 빈이라 프록시 호출이 정상 동작 → C3(자기호출) 문제 없음.
            //    영향 지역만 도므로(태풍도 ~250개 한정) long-tx 없음.
            affectedSigungu.forEach(riskService::recomputeRegionRisk);

        } catch (Exception e) {
            // 위험도 실패가 수집/클러스터링 파이프라인에 영향 주지 않도록 격리.
            log.error("위험도 재계산 실패 eventId={} alertId={}", event.eventId(), event.alertId(), e);
        }
    }
}
