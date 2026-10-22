package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 저장된 재난문자를 분류해 alert_kind 에 기록한다. 수집 스케줄러가 새 알림마다 부르며,
 * 실패는 null(미분류)로 남기고 예외를 밖으로 내보내지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertKindService {

    private final DisasterAlertRepository disasterAlertRepository;
    private final AlertKindClassifier alertKindClassifier;

    // @Transactional 없음 — LLM 호출 동안 DB 커넥션을 물고 있지 않으려는 것이라 save 를 직접 호출한다.
    public void classifyAndSave(Long alertId) {
        try {
            // 1) 읽기 → 2) 분류 → 3) 값이 있을 때만 저장(empty 는 미분류 null 유지)
            disasterAlertRepository.findById(alertId).ifPresent(alert ->
                    alertKindClassifier.classify(alert.getDisasterType(), alert.getMessage()).ifPresent(kind -> {
                        alert.classifyAs(kind);
                        disasterAlertRepository.save(alert);
                    }));
        } catch (Exception e) {
            // 4) DB 장애 등도 삼킨다 — 분류가 수집 루프를 끊으면 안 된다. 미분류 알림은 이 로그의 alertId 로 찾는다.
            log.warn("알림 분류 저장 실패, 미분류로 둡니다: alertId={}, {}", alertId, e.toString());
        }
    }
}
