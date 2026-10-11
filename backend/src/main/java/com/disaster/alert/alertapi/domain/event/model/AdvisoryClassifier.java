package com.disaster.alert.alertapi.domain.event.model;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;

import java.util.regex.Pattern;

/**
 * 유형별 안내성 분류 진입점. 안내성 분리 대상은 산불({@link FireAlertClassifier})과
 * 유형 "기타"의 물놀이·수상안전 예방 안내문이다.
 *
 * <p>폭염·한파는 안내를 분리하지 않는다 — 에피소드형 유형으로 "유형 + 시군구" 하나의 이벤트에 안내와 특보를
 * 함께 합친다(EventClusteringService.clusterEpisodicType).
 */
public final class AdvisoryClassifier {

    // 안내 신호와 예방 어휘가 모두 있어야 안내문으로 본다 — 둘 중 하나만으로는 사건 문자와 구분이 안 된다.
    private static final Pattern WATER_TOPIC = Pattern.compile("물놀이|수상안전|수상 안전|수난사고|구명조끼|입수 금지");
    private static final Pattern PREVENTION = Pattern.compile("예방|안전수칙|준수|금지|착용");
    // 사건 신호가 하나라도 있으면 안내가 아니다 — 실제 사고를 안내로 분리하면 사건이 묻히는 쪽이 더 위험해 보수적으로 둔다.
    private static final Pattern INCIDENT = Pattern.compile("익수|익사|실종|수색|사망|구조 중|구조중|구조하");

    private AdvisoryClassifier() {
    }

    public static boolean isAdvisory(String disasterType, String message, DisasterLevel level) {
        if ("산불".equals(disasterType)) {
            return FireAlertClassifier.isAdvisory(message, level);
        }
        if ("기타".equals(disasterType)) {
            return isWaterSafetyGuide(message, level);
        }
        return false;
    }

    private static boolean isWaterSafetyGuide(String message, DisasterLevel level) {
        if (level == DisasterLevel.LEVEL_2 || level == DisasterLevel.LEVEL_3) {
            return false;
        }
        if (message == null || message.isBlank()) {
            return false;
        }
        return WATER_TOPIC.matcher(message).find()
                && PREVENTION.matcher(message).find()
                && !INCIDENT.matcher(message).find();
    }
}
