package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 저장된 재난문자를 분류해 alert_kind 에 기록한다.
 */
// Red 단계 뼈대 — 구현은 Green 에서
@Service
@RequiredArgsConstructor
public class AlertKindService {

    private final DisasterAlertRepository disasterAlertRepository;
    private final AlertKindClassifier alertKindClassifier;

    public void classifyAndSave(Long alertId) {
        // 아무것도 하지 않는다.
    }
}
