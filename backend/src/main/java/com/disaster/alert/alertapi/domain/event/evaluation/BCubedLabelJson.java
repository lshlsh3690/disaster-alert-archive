package com.disaster.alert.alertapi.domain.event.evaluation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;

/**
 * B-cubed 정답 JSON 텍스트를 BCubedGold 로 바꾼다. 형식 오류·검토 미완료는 IllegalArgumentException.
 */
// Red 단계 뼈대 — 구현은 Green 에서
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BCubedLabelJson {

    public static BCubedGold parse(String jsonText) {
        return new BCubedGold(Map.of(), Set.of());
    }
}
