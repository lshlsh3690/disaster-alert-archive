package com.disaster.alert.alertapi.domain.event.evaluation;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 알림 한 건의 값. 사실 계산기({@link AlertPlaceFacts})의 입력이자, 재확인 후보 목록·판사 일치율 리포트·일일 채점이
 * 두 알림을 보여 주거나 판사 입력을 만들 때 쓴다.
 *
 * @param sigunguCodes 수신 지역 법정동 코드 앞 5자리(중복 제거). 라벨 JSON 에는 지역 이름만 있어 DB 에서 읽는다
 */
public record AlertSnapshot(long id, LocalDateTime sentAt, String type, String message, List<String> sigunguCodes) {
}
