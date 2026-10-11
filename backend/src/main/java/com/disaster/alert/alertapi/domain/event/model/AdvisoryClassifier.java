package com.disaster.alert.alertapi.domain.event.model;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 유형별 안내성 분류 진입점. 산불은 {@link FireAlertClassifier}, 폭염·한파는 여기서 판정한다.
 *
 * <p>폭염·한파 일반 안내(행동요령)는 매일 같은 시군구에 반복돼 사건 이벤트를 늘리므로 안내 이벤트로 모은다.
 * 방향은 산불과 같이 보수적이되 <b>판단 불가는 안내가 아니다</b>(산불은 본문 null=안내, 여기는 반대):
 * 안내로 잘못 분류해도 라벨만 바뀌는 soft loss 지만, 폭염·한파는 특보 발효 문구가 사건 경로(임베딩)에
 * 남아야 기존 동작이 보존되므로 특보·피해 표현이 하나라도 있으면 사건으로 둔다.
 * 피해 표현은 "사고"·"피해" 같은 일반 단어가 아니라 실제 발생을 말하는 표현(사망, 사상자·부상자, 환자 발생)만 신호로 본다.
 */
public final class AdvisoryClassifier {

    private static final Set<String> WEATHER_TYPES = Set.of("폭염", "한파");

    /** 특보 자체를 알리는 표현 — 안내 표현이 섞여 있어도 사건 경로를 유지한다. */
    private static final Pattern WARNING_EXPRESSION =
            Pattern.compile("발효|발령|해제|격상|주의보|경보|특보");

    /**
     * 실제 발생을 말하는 피해 표현 — 사망, 사상자·부상자, "환자(온열질환자·한랭질환자 포함)가/N명 발생".
     * "사고"·"피해"·"정전" 단독은 "안전사고 주의"·"피해 예방"·"정전 시 …" 같은 안내문 상투구라 신호로 쓰지 않는다
     * (실데이터에서 폭염 317건·한파 397건의 일반 안내가 이 단어들 때문에 사건으로 샜다).
     */
    private static final Pattern DAMAGE_EXPRESSION =
            Pattern.compile("사망|사상자|부상자|환자(가|는)?\\s*(\\d+명\\s*)?발생");

    private AdvisoryClassifier() {
    }

    public static boolean isAdvisory(String disasterType, String message, DisasterLevel level) {
        if ("산불".equals(disasterType)) {
            return FireAlertClassifier.isAdvisory(message, level);
        }
        if (disasterType == null || !WEATHER_TYPES.contains(disasterType)) {
            return false;
        }
        if (level == DisasterLevel.LEVEL_2 || level == DisasterLevel.LEVEL_3) {
            return false;
        }
        if (message == null || message.isBlank()) {
            return false;
        }
        return !WARNING_EXPRESSION.matcher(message).find()
                && !DAMAGE_EXPRESSION.matcher(message).find();
    }
}
