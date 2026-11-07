package com.disaster.alert.alertapi.domain.event.evaluation;

/**
 * 리포트(마크다운)의 틀린 쌍 표에서 읽어 낸 한 줄: 알림 id 둘과 종류.
 * 옛 리포트에는 "종류" 칸이 없으므로 그때는 {@link Kind#UNKNOWN} 이다.
 */
// Red 단계 뼈대 — 구현은 Green 에서
public record ReportedPair(long alertA, long alertB, Kind kind) {

    // 놓침, 잘못 묶음, 리포트에 종류 칸이 없어 알 수 없음
    public enum Kind { MISSED, OVER_MERGED, UNKNOWN }
}
