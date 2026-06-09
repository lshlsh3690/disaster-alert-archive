package com.disaster.alert.alertapi.domain.risk.event;

/**
 * 알림이 이벤트에 클러스터링 완료되었을 때 발행되는 도메인 이벤트.
 *
 * <p>EventClusteringService 의 createNewEvent / mergeIntoExisting 끝에서
 * ApplicationEventPublisher 로 발행한다. 위험도 모듈은 이걸 구독.
 */
public record AlertClusteredEvent(Long eventId, Long alertId) {
}
