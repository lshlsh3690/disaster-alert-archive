package com.disaster.alert.alertapi.domain.event.model;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;

// Red 단계 뼈대 — 구현은 Green 에서
public final class AdvisoryClassifier {

    private AdvisoryClassifier() {
    }

    public static boolean isAdvisory(String disasterType, String message, DisasterLevel level) {
        return false;
    }
}
