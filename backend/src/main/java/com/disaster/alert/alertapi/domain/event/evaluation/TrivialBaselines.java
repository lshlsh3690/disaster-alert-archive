package com.disaster.alert.alertapi.domain.event.evaluation;

import java.util.List;
import java.util.OptionalDouble;

/** 판단 없이 전부 묶거나 전부 나눌 때의 결과를 같은 평가 집합에서 만드는 순수 계산기. */
public final class TrivialBaselines {

    private TrivialBaselines() {
    }

    /** 실제 평가에서 제외한 쌍은 그대로 제외하고, 유효한 쌍을 전부 한 이벤트에 둔 결과를 만든다. */
    public static PairCountResult allMerged(PairCountResult counts) {
        // 1) 시스템을 잰 것과 같은 집합만 사용한다. 모르겠음·미배정은 분모에 더하지 않는다.
        int same = counts.sameEventPairs();
        int different = counts.differentEventPairs();
        // 2) 같은 사건은 전부 맞게 묶고 다른 사건은 전부 과병합한다. 실제 틀린 쌍 표는 만들지 않는다.
        return new PairCountResult(same, 0, different, 0, counts.excludedUnsure(), counts.excludedUnassigned(),
                same == 0 ? OptionalDouble.empty() : OptionalDouble.of(0),
                same + different == 0 ? OptionalDouble.empty() : OptionalDouble.of(different * 100.0 / (same + different)),
                List.of());
    }

    /** 같은 유효 쌍을 전부 다른 이벤트에 둔 결과. 묶은 쌍이 없어 과병합율은 계산 불가다. */
    public static PairCountResult allSeparated(PairCountResult counts) {
        // 1) 같은 사건은 전부 과소병합하고 다른 사건은 전부 맞게 나눈다. 제외 건수는 출처와 같게 둔다.
        int same = counts.sameEventPairs();
        int different = counts.differentEventPairs();
        return new PairCountResult(0, same, 0, different, counts.excludedUnsure(), counts.excludedUnassigned(),
                same == 0 ? OptionalDouble.empty() : OptionalDouble.of(100), OptionalDouble.empty(), List.of());
    }
}
