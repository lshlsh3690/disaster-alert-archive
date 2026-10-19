package com.disaster.alert.alertapi.domain.event.evaluation;

/**
 * 오류 쌍에 붙이는 사실 정보. 원인을 추정하지 않고 보이는 값만 담는다.
 *
 * @param types 두 알림의 재난 유형. 같으면 하나, 다르면 "A/B".
 * @param sameSigungu 법정동 코드 앞 5자리가 하나라도 같은가.
 * @param gapHours 발송 간격(시간, 반올림 정수).
 * @param textDistance 본문 임베딩 코사인 거리. 임베딩이 없으면 null.
 */
public record PairFacts(String types, boolean sameSigungu, long gapHours, Double textDistance) {
}
