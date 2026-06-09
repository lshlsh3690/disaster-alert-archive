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
 * 위험도 전용 스레드 풀({@code AsyncConfig#riskTaskExecutor})을 사용하여,
 * 클러스터링과 분리된 스레드에서 무거운 위험도 계산이 비동기로 안전하게 처리된다.
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

            // 2. 영향받은 시군구의 자기 위험도(source) 재계산 (문자 도착 시 실시간 갱신).
            //    리스너는 외부 빈이라 프록시 호출이 정상 동작 → C3(자기호출) 문제 없음.
            affectedSigungu.forEach(riskService::recomputeRegionSource);

            // 3. 인접 그래프 전파로 effective(risk_score) 갱신.
            //    이웃까지 번지므로 영향 지역만이 아니라 전체 전파 1회(전 그래프 ~ms).
            if (!affectedSigungu.isEmpty()) {
                riskService.propagateEffective();
            }

        } catch (Exception e) {
            // 위험도 실패가 수집/클러스터링 파이프라인에 영향 주지 않도록 격리.
            log.error("위험도 재계산 실패 eventId={} alertId={}", event.eventId(), event.alertId(), e);
        }
    }
}
