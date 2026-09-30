package com.disaster.alert.alertapi.domain.notification.dto;

import com.disaster.alert.alertapi.domain.notification.model.NotificationType;

/**
 * 회원 여러 명의 알림 설정을 한 번에 조회({@link com.disaster.alert.alertapi.domain.notification.repository.NotificationPreferenceRepository#findNotificationTypesByMemberIdIn})하기
 * 위한 JPQL 생성자 프로젝션 결과 행.
 *
 * <p>엔티티가 아니라 이 record 로 받아 영속성 컨텍스트에 쌓이지 않게 하는 것이 목적.
 * 리포지토리 인터페이스 안에 중첩 record 로 두면 Hibernate 의 HQL {@code new} 생성자
 * 표현식이 중첩 클래스를 못 찾아 애플리케이션 기동 자체가 실패한다({@code Could not resolve
 * class '...Repository.MemberNotificationType' named for instantiation}) — 반드시 최상위
 * 클래스여야 한다.
 */
public record MemberNotificationType(Long memberId, NotificationType notificationType) {
}
