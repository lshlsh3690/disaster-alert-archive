package com.disaster.alert.alertapi.domain.event.model;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;

/**
 * 유형별 안내성 분류 진입점. 안내성 분리는 산불({@link FireAlertClassifier})만 대상이다.
 *
 * <p>폭염·한파는 안내를 분리하지 않는다 — 에피소드형 유형으로 "유형 + 시군구" 하나의 이벤트에 안내와 특보를
 * 함께 합친다(EventClusteringService.clusterEpisodicType).
 */
public final class AdvisoryClassifier {

    private AdvisoryClassifier() {
    }

    public static boolean isAdvisory(String disasterType, String message, DisasterLevel level) {
        if ("산불".equals(disasterType)) {
            return FireAlertClassifier.isAdvisory(message, level);
        }
        return false;
    }
}
